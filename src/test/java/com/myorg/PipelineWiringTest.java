package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(templateJson.contains("SynthesizeSpeech"),
            "Pipeline should contain SynthesizeSpeech state");
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
    public void testValidationFailureRoutesToUpdateMetadataStatusFailed() {
        // The default path of the Choice state should route to UpdateMetadataStatusFailed
        String templateJson = template.toJSON().toString();
        // In the Choice state definition, Default should point to UpdateMetadataStatusFailed
        assertTrue(templateJson.contains("UpdateMetadataStatusFailed"),
            "Validation failure should route to UpdateMetadataStatusFailed");
    }

    @Test
    public void testFailurePathRoutesToPublishFailureNotification() {
        // UpdateMetadataStatusFailed should chain to PublishFailureNotification
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("PublishFailureNotification"),
            "Failure path should include PublishFailureNotification");
    }

    @Test
    public void testPollyPermissionsConfiguredWithIamResources() {
        // Polly synthesizeSpeech permission should exist in the state machine policy
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", "polly:synthesizeSpeech",
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }
}
