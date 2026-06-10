package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class AudioProcessingTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testLambdaHasOutputBucketEnvironmentVariable() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Environment", Match.objectLike(Map.of(
                "Variables", Match.objectLike(Map.of(
                    "OUTPUT_BUCKET_NAME", Match.anyValue()
                ))
            ))
        )));
    }

    @Test
    public void testLambdaHasInputBucketEnvironmentVariable() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Environment", Match.objectLike(Map.of(
                "Variables", Match.objectLike(Map.of(
                    "INPUT_BUCKET_NAME", Match.anyValue()
                ))
            ))
        )));
    }

    @Test
    public void testLambdaHasTableNameEnvironmentVariable() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Environment", Match.objectLike(Map.of(
                "Variables", Match.objectLike(Map.of(
                    "TABLE_NAME", Match.anyValue()
                ))
            ))
        )));
    }

    @Test
    public void testLambdaHasPollyPermissions() {
        // Lambda role should have polly:SynthesizeSpeech permission
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", "polly:SynthesizeSpeech",
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaHasS3ReadPermissionOnInputBucket() {
        // Lambda role should have s3:GetObject permission on input bucket (part of grantRead array)
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of("s3:GetObject*", "s3:GetBucket*", "s3:List*")),
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaHasS3WritePermissionOnOutputBucket() {
        // Lambda role should have s3:PutObject permission on output bucket
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of("s3:PutObject")),
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaHasDynamoDbReadWritePermissions() {
        // Lambda role should have DynamoDB read/write permissions (from grantReadWriteData)
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:BatchGetItem",
                            "dynamodb:Query",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:PutItem",
                            "dynamodb:UpdateItem",
                            "dynamodb:DeleteItem",
                            "dynamodb:DescribeTable"
                        )),
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testStateMachineDoesNotContainDirectPollyCall() throws Exception {
        // The state machine should NOT have a direct CallAwsService for polly:synthesizeSpeech
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode pollyState = states.get("SynthesizeSpeech");
        assertNull(pollyState, "State machine should NOT contain a SynthesizeSpeech state");
    }

    @Test
    public void testStateMachineChainOrder() throws Exception {
        // Chain should be: PutMetadataRecord -> ValidateFileExtension -> ProcessAudioMetadata -> UpdateMetadataStatus -> PublishSuccessNotification
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        // PutMetadataRecord -> ValidateFileExtension
        JsonNode putMetadata = states.get("PutMetadataRecord");
        assertNotNull(putMetadata, "PutMetadataRecord state should exist");
        assertEquals("ValidateFileExtension", putMetadata.get("Next").asText());

        // ValidateFileExtension routes valid extensions to ProcessAudioMetadata
        JsonNode validateExt = states.get("ValidateFileExtension");
        assertNotNull(validateExt, "ValidateFileExtension state should exist");
        JsonNode choices = validateExt.get("Choices");
        boolean routesToProcessAudio = false;
        for (JsonNode choice : choices) {
            if ("ProcessAudioMetadata".equals(choice.get("Next").asText())) {
                routesToProcessAudio = true;
                break;
            }
        }
        assertTrue(routesToProcessAudio,
            "ValidateFileExtension should route to ProcessAudioMetadata");

        // ProcessAudioMetadata -> UpdateMetadataStatus (no more SynthesizeSpeech in between)
        JsonNode processAudio = states.get("ProcessAudioMetadata");
        assertNotNull(processAudio, "ProcessAudioMetadata state should exist");
        assertEquals("UpdateMetadataStatus", processAudio.get("Next").asText(),
            "ProcessAudioMetadata should chain directly to UpdateMetadataStatus");

        // UpdateMetadataStatus -> PublishSuccessNotification
        JsonNode updateStatus = states.get("UpdateMetadataStatus");
        assertNotNull(updateStatus, "UpdateMetadataStatus state should exist");
        assertEquals("PublishSuccessNotification", updateStatus.get("Next").asText());

        // PublishSuccessNotification is terminal
        JsonNode publishSuccess = states.get("PublishSuccessNotification");
        assertNotNull(publishSuccess, "PublishSuccessNotification state should exist");
        assertTrue(publishSuccess.get("End").asBoolean());
    }

    @Test
    public void testLambdaResultFeedsIntoUpdateMetadataStatus() throws Exception {
        // The ProcessAudioMetadata Lambda should store result at $.lambdaResult
        // and UpdateMetadataStatus should reference output location from Lambda result
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        JsonNode processAudio = states.get("ProcessAudioMetadata");
        assertEquals("$.lambdaResult", processAudio.get("ResultPath").asText(),
            "ProcessAudioMetadata should store result at $.lambdaResult");

        // UpdateMetadataStatus should reference $.lambdaResult for output metadata
        JsonNode updateStatus = states.get("UpdateMetadataStatus");
        assertNotNull(updateStatus, "UpdateMetadataStatus state should exist");
        String updateStateJson = updateStatus.toString();
        assertTrue(updateStateJson.contains("lambdaResult"),
            "UpdateMetadataStatus should reference lambdaResult for output metadata");
    }

    /**
     * Extracts and parses the state machine DefinitionString from the synthesized template.
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
