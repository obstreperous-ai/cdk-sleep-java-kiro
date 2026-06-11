package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive end-to-end validation tests for the Sleep Audio Processing Pipeline.
 * These tests verify cross-cutting concerns including resource integrity, data flow,
 * notification payloads, retry configuration, error handling, and IAM permissions.
 */
public class ComprehensiveEndToEndTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testCompletePipelineResourceIntegrity() {
        // Validate all expected resource types exist with correct counts
        template.resourceCountIs("AWS::S3::Bucket", 2);
        template.resourceCountIs("AWS::DynamoDB::Table", 1);
        template.resourceCountIs("AWS::StepFunctions::StateMachine", 1);
        template.resourceCountIs("AWS::Events::Rule", 1);
        template.resourceCountIs("AWS::SNS::Topic", 2);
        template.resourceCountIs("AWS::KMS::Key", 1);
        template.resourceCountIs("AWS::CloudWatch::Alarm", 2);
        template.resourceCountIs("AWS::CloudWatch::Dashboard", 1);

        // At least 1 Lambda function (audio processor + notifications handler)
        ObjectMapper mapper = new ObjectMapper();
        try {
            String templateJson = mapper.writeValueAsString(template.toJSON());
            JsonNode root = mapper.readTree(templateJson);
            JsonNode resources = root.get("Resources");
            int lambdaCount = 0;
            for (java.util.Iterator<String> it = resources.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                if ("AWS::Lambda::Function".equals(resources.get(key).get("Type").asText())) {
                    lambdaCount++;
                }
            }
            assertTrue(lambdaCount >= 1,
                "Pipeline should have at least 1 Lambda function, found: " + lambdaCount);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse template for Lambda count check", e);
        }
    }

    @Test
    public void testHappyPathDynamoDbUpdatesContainOutputMetadata() throws Exception {
        // Verify UpdateMetadataStatus parameters include outputBucket, outputKey, outputUri
        // from $.lambdaResult
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode updateState = states.get("UpdateMetadataStatus");
        assertNotNull(updateState, "UpdateMetadataStatus state should exist");

        JsonNode parameters = updateState.get("Parameters");
        assertNotNull(parameters, "UpdateMetadataStatus should have Parameters");

        JsonNode expressionValues = parameters.get("ExpressionAttributeValues");
        assertNotNull(expressionValues, "Should have ExpressionAttributeValues");

        // Verify outputBucket comes from $.lambdaResult.outputBucket
        JsonNode outputBucket = expressionValues.get(":outputBucket");
        assertNotNull(outputBucket, "Should have :outputBucket expression value");
        assertEquals("$.lambdaResult.outputBucket", outputBucket.get("S.$").asText(),
            "outputBucket should reference $.lambdaResult.outputBucket");

        // Verify outputKey comes from $.lambdaResult.outputKey
        JsonNode outputKey = expressionValues.get(":outputKey");
        assertNotNull(outputKey, "Should have :outputKey expression value");
        assertEquals("$.lambdaResult.outputKey", outputKey.get("S.$").asText(),
            "outputKey should reference $.lambdaResult.outputKey");

        // Verify outputUri comes from $.lambdaResult.outputUri
        JsonNode outputUri = expressionValues.get(":outputUri");
        assertNotNull(outputUri, "Should have :outputUri expression value");
        assertEquals("$.lambdaResult.outputUri", outputUri.get("S.$").asText(),
            "outputUri should reference $.lambdaResult.outputUri");

        // Verify UpdateExpression includes the output fields
        String updateExpression = parameters.get("UpdateExpression").asText();
        assertTrue(updateExpression.contains("#ob = :outputBucket"),
            "UpdateExpression should set outputBucket");
        assertTrue(updateExpression.contains("#ok = :outputKey"),
            "UpdateExpression should set outputKey");
        assertTrue(updateExpression.contains("#ou = :outputUri"),
            "UpdateExpression should set outputUri");
    }

    @Test
    public void testSuccessNotificationContainsAudioIdAndStatus() throws Exception {
        // Verify PublishSuccessNotification message payload contains audioId and status=COMPLETED
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode publishSuccess = states.get("PublishSuccessNotification");
        assertNotNull(publishSuccess, "PublishSuccessNotification state should exist");

        JsonNode parameters = publishSuccess.get("Parameters");
        assertNotNull(parameters, "PublishSuccessNotification should have Parameters");

        JsonNode message = parameters.get("Message");
        assertNotNull(message, "PublishSuccessNotification should have Message parameter");

        // audioId should be a JsonPath reference to the object key
        String audioIdPath = message.get("audioId.$").asText();
        assertNotNull(audioIdPath, "Message should contain audioId.$ path expression");
        assertEquals("$.object.key", audioIdPath,
            "audioId should reference $.object.key");

        // status should be COMPLETED
        String status = message.get("status").asText();
        assertEquals("COMPLETED", status,
            "Success notification status should be COMPLETED");
    }

    @Test
    public void testFailureNotificationContainsErrorDetails() throws Exception {
        // Verify PublishFailureNotification message payload contains audioId, status=FAILED,
        // error, and cause fields
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode publishFailure = states.get("PublishFailureNotification");
        assertNotNull(publishFailure, "PublishFailureNotification state should exist");

        JsonNode parameters = publishFailure.get("Parameters");
        assertNotNull(parameters, "PublishFailureNotification should have Parameters");

        JsonNode message = parameters.get("Message");
        assertNotNull(message, "PublishFailureNotification should have Message parameter");

        // audioId should reference the object key
        String audioIdPath = message.get("audioId.$").asText();
        assertEquals("$.object.key", audioIdPath,
            "Failure notification audioId should reference $.object.key");

        // status should be FAILED
        String status = message.get("status").asText();
        assertEquals("FAILED", status,
            "Failure notification status should be FAILED");

        // error should reference $.error.Error
        String errorPath = message.get("error.$").asText();
        assertEquals("$.error.Error", errorPath,
            "Failure notification should include error from $.error.Error");

        // cause should reference $.error.Cause
        String causePath = message.get("cause.$").asText();
        assertEquals("$.error.Cause", causePath,
            "Failure notification should include cause from $.error.Cause");
    }

    @Test
    public void testAllCriticalTasksHaveRetryConfiguration() throws Exception {
        // Verify PutMetadataRecord, ProcessAudioMetadata, UpdateMetadataStatus,
        // PublishSuccessNotification, and PublishFailureNotification all have Retry blocks
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        String[] criticalTasks = {
            "PutMetadataRecord",
            "ProcessAudioMetadata",
            "UpdateMetadataStatus",
            "PublishSuccessNotification",
            "PublishFailureNotification"
        };

        for (String taskName : criticalTasks) {
            JsonNode state = states.get(taskName);
            assertNotNull(state, taskName + " state should exist");

            JsonNode retriers = state.get("Retry");
            assertNotNull(retriers, taskName + " should have Retry configuration");
            assertTrue(retriers.size() > 0,
                taskName + " should have at least one Retry entry");

            // Verify each retry has required fields
            for (JsonNode retrier : retriers) {
                assertNotNull(retrier.get("ErrorEquals"),
                    taskName + " retry should have ErrorEquals");
                assertNotNull(retrier.get("MaxAttempts"),
                    taskName + " retry should have MaxAttempts");
                assertNotNull(retrier.get("BackoffRate"),
                    taskName + " retry should have BackoffRate");
            }
        }
    }

    @Test
    public void testValidationErrorPassStateInjectsErrorContext() throws Exception {
        // Verify the ValidationError Pass state has Result with Error and Cause fields
        // and resultPath $.error
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode validationError = states.get("ValidationError");
        assertNotNull(validationError, "ValidationError state should exist");

        // Type should be Pass
        assertEquals("Pass", validationError.get("Type").asText(),
            "ValidationError should be a Pass state");

        // Result should contain Error and Cause
        JsonNode result = validationError.get("Result");
        assertNotNull(result, "ValidationError should have a Result");
        assertEquals("ValidationError", result.get("Error").asText(),
            "ValidationError Result should have Error='ValidationError'");
        assertNotNull(result.get("Cause"),
            "ValidationError Result should have a Cause field");
        assertTrue(result.get("Cause").asText().length() > 0,
            "ValidationError Cause should not be empty");

        // ResultPath should be $.error
        assertEquals("$.error", validationError.get("ResultPath").asText(),
            "ValidationError should use $.error as ResultPath");

        // Next should chain to UpdateMetadataStatusFailed
        assertEquals("UpdateMetadataStatusFailed", validationError.get("Next").asText(),
            "ValidationError should chain to UpdateMetadataStatusFailed");
    }

    @Test
    public void testEventBridgeToStateMachineIntegration() {
        // Verify the EventBridge rule targets the state machine and uses $.detail input
        template.hasResourceProperties("AWS::Events::Rule", Match.objectLike(Map.of(
            "EventPattern", Match.objectLike(Map.of(
                "source", List.of("aws.s3"),
                "detail-type", List.of("Object Created")
            )),
            "State", "ENABLED",
            "Targets", Match.arrayWith(List.of(
                Match.objectLike(Map.of(
                    "Arn", Match.anyValue(),
                    "InputPath", "$.detail",
                    "RoleArn", Match.anyValue()
                ))
            ))
        )));
    }

    @Test
    public void testStateMachineHasRequiredIamPermissions() throws Exception {
        // Verify IAM policies grant dynamodb:putItem, dynamodb:updateItem,
        // sns:Publish, lambda:InvokeFunction scoped to resource ARNs (not "*")
        ObjectMapper mapper = new ObjectMapper();

        // Use findResources to get all IAM Policy resources as a Map
        java.util.Map<String, java.util.Map<String, Object>> policies =
            template.findResources("AWS::IAM::Policy");

        String policiesJson = mapper.writeValueAsString(policies);
        JsonNode policiesNode = mapper.readTree(policiesJson);

        boolean hasDynamoDbPutItem = false;
        boolean hasDynamoDbUpdateItem = false;
        boolean hasLambdaPermission = false;
        boolean hasSnsPermission = false;

        for (java.util.Iterator<String> it = policiesNode.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            JsonNode policy = policiesNode.get(key);
            JsonNode statements = policy.get("Properties")
                .get("PolicyDocument").get("Statement");

            for (JsonNode stmt : statements) {
                String effect = stmt.get("Effect").asText();
                if (!"Allow".equals(effect)) continue;

                JsonNode actionNode = stmt.get("Action");
                JsonNode resourceNode = stmt.get("Resource");

                if (actionNode.isTextual()) {
                    String action = actionNode.asText();

                    // Check for DynamoDB putItem
                    if ("dynamodb:putItem".equals(action)) {
                        hasDynamoDbPutItem = true;
                        assertTrue(!resourceNode.isTextual() || !"*".equals(resourceNode.asText()),
                            "DynamoDB putItem permission should be scoped to specific resource ARN");
                    }

                    // Check for DynamoDB updateItem
                    if ("dynamodb:updateItem".equals(action)) {
                        hasDynamoDbUpdateItem = true;
                        assertTrue(!resourceNode.isTextual() || !"*".equals(resourceNode.asText()),
                            "DynamoDB updateItem permission should be scoped to specific resource ARN");
                    }

                    // Check for lambda:InvokeFunction
                    if ("lambda:InvokeFunction".equals(action)) {
                        hasLambdaPermission = true;
                        assertTrue(!resourceNode.isTextual() || !"*".equals(resourceNode.asText()),
                            "Lambda invoke permission should be scoped to specific resource ARN");
                    }

                    // Check for sns:Publish
                    if ("sns:Publish".equals(action)) {
                        hasSnsPermission = true;
                        assertTrue(!resourceNode.isTextual() || !"*".equals(resourceNode.asText()),
                            "SNS publish permission should be scoped to specific resource ARN");
                    }
                }
            }
        }

        assertTrue(hasDynamoDbPutItem,
            "State machine should have dynamodb:putItem permission");
        assertTrue(hasDynamoDbUpdateItem,
            "State machine should have dynamodb:updateItem permission");
        assertTrue(hasLambdaPermission,
            "State machine should have lambda:InvokeFunction permission");
        assertTrue(hasSnsPermission,
            "State machine should have sns:Publish permission");
    }

    /**
     * Extracts and parses the state machine DefinitionString from the synthesized template.
     * The DefinitionString uses Fn::Join with intrinsic references (Ref, Fn::GetAtt) that
     * appear as non-text parts. These are replaced with placeholder strings so the JSON
     * can be parsed to inspect state machine structure.
     */
    private JsonNode getStateMachineDefinition() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String templateJson = mapper.writeValueAsString(template.toJSON());
        JsonNode root = mapper.readTree(templateJson);
        JsonNode resources = root.get("Resources");
        for (java.util.Iterator<String> it = resources.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            JsonNode resource = resources.get(key);
            if ("AWS::StepFunctions::StateMachine".equals(resource.get("Type").asText())) {
                JsonNode definitionString = resource.get("Properties").get("DefinitionString");
                if (definitionString.has("Fn::Join")) {
                    JsonNode parts = definitionString.get("Fn::Join").get(1);
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (part.isTextual()) {
                            sb.append(part.asText());
                        } else {
                            sb.append("PLACEHOLDER");
                        }
                    }
                    return mapper.readTree(sb.toString());
                } else if (definitionString.isTextual()) {
                    return mapper.readTree(definitionString.asText());
                }
            }
        }
        throw new RuntimeException("StateMachine resource not found in template");
    }
}
