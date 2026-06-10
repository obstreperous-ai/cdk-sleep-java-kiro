package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class PipelineWiringTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testEventBridgeRuleTargetsStateMachine() {
        // EventBridge rule should target the state machine
        template.hasResourceProperties("AWS::Events::Rule", Match.objectLike(Map.of(
            "Targets", Match.arrayWith(List.of(
                Match.objectLike(Map.of(
                    "Arn", Match.anyValue()
                ))
            ))
        )));
    }

    @Test
    public void testStateMachineContainsAllExpectedStates() {
        // State machine definition should contain all expected states in the pipeline
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("PutMetadataRecord"),
            "Pipeline should contain PutMetadataRecord state");
        assertTrue(templateJson.contains("ValidateFileExtension"),
            "Pipeline should contain ValidateFileExtension state");
        assertTrue(templateJson.contains("ProcessAudioMetadata"),
            "Pipeline should contain ProcessAudioMetadata state");
        assertTrue(templateJson.contains("UpdateMetadataStatus"),
            "Pipeline should contain UpdateMetadataStatus state");
        assertTrue(templateJson.contains("PublishSuccessNotification"),
            "Pipeline should contain PublishSuccessNotification state");
    }

    @Test
    public void testErrorPathContainsFailureStates() {
        // Error/failure path should contain UpdateMetadataStatusFailed and PublishFailureNotification
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("UpdateMetadataStatusFailed"),
            "Pipeline should contain UpdateMetadataStatusFailed state");
        assertTrue(templateJson.contains("PublishFailureNotification"),
            "Pipeline should contain PublishFailureNotification state");
    }

    @Test
    public void testDynamoDbPermissionsScopedToTableArn() {
        // DynamoDB permissions should be scoped to the table ARN (not *)
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", "dynamodb:putItem",
                        "Effect", "Allow",
                        "Resource", Match.not(Match.exact("*"))
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testSnsPublishPermissionsScopedToTopicArns() {
        // SNS publish permissions should be scoped to specific topic ARNs
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", "sns:Publish",
                        "Effect", "Allow",
                        "Resource", Match.not(Match.exact("*"))
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaInvokePermissionScopedToFunctionArn() {
        // Lambda invoke permissions should be scoped to the function ARN
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", "lambda:InvokeFunction",
                        "Effect", "Allow",
                        "Resource", Match.not(Match.exact("*"))
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testStateMachineHasCloudWatchLoggingAtAllLevel() {
        // State machine should have CloudWatch logging enabled at ALL level
        template.hasResourceProperties("AWS::StepFunctions::StateMachine", Match.objectLike(Map.of(
            "LoggingConfiguration", Match.objectLike(Map.of(
                "Level", "ALL"
            ))
        )));
    }

    @Test
    public void testValidationFailureRoutesToUpdateMetadataStatusFailed() throws Exception {
        // Parse the state machine DefinitionString and verify the Choice Default field
        // points to the ValidationError Pass state (which then chains to UpdateMetadataStatusFailed)
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode choiceState = states.get("ValidateFileExtension");
        String defaultTarget = choiceState.get("Default").asText();
        assertEquals("ValidationError", defaultTarget,
            "Choice state Default should route to ValidationError Pass state");

        // Verify ValidationError then routes to UpdateMetadataStatusFailed
        JsonNode validationErrorState = states.get("ValidationError");
        assertEquals("UpdateMetadataStatusFailed", validationErrorState.get("Next").asText(),
            "ValidationError Pass state should route to UpdateMetadataStatusFailed");
    }

    @Test
    public void testFailurePathRoutesToPublishFailureNotification() throws Exception {
        // Parse the state machine DefinitionString and verify UpdateMetadataStatusFailed.Next
        // equals PublishFailureNotification
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode failedState = states.get("UpdateMetadataStatusFailed");
        assertEquals("PublishFailureNotification", failedState.get("Next").asText(),
            "UpdateMetadataStatusFailed should chain to PublishFailureNotification");
    }

    @Test
    public void testPutMetadataRecordRoutesToValidateFileExtension() throws Exception {
        // Parse the state machine DefinitionString and verify PutMetadataRecord.Next
        // equals ValidateFileExtension
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode putMetadataState = states.get("PutMetadataRecord");
        assertEquals("ValidateFileExtension", putMetadataState.get("Next").asText(),
            "PutMetadataRecord should chain to ValidateFileExtension");
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
                            // Intrinsic function reference (Ref, Fn::GetAtt) - these appear
                            // inside JSON string values, so insert a plain placeholder string
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
