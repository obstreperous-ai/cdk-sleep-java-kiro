package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

public class StepFunctionsTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testStateMachineExists() {
        template.resourceCountIs("AWS::StepFunctions::StateMachine", 1);
    }

    @Test
    public void testStateMachineHasLogging() {
        template.hasResourceProperties("AWS::StepFunctions::StateMachine", Match.objectLike(Map.of(
            "LoggingConfiguration", Match.objectLike(Map.of(
                "Level", "ALL"
            ))
        )));
    }

    @Test
    public void testStateMachineDefinitionContainsLambdaProcessingTask() {
        // Verify the state machine definition references the Lambda processing task
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("ProcessAudioMetadata"),
            "State machine definition should reference ProcessAudioMetadata Lambda task");
    }

    @Test
    public void testEventBridgeRuleTargetsStateMachine() {
        template.hasResourceProperties("AWS::Events::Rule", Match.objectLike(Map.of(
            "Targets", Match.arrayWith(List.of(
                Match.objectLike(Map.of(
                    "Arn", Match.objectLike(Map.of(
                        "Ref", Match.anyValue()
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaRoleHasPollyPermissions() {
        // Polly permissions should be on the Lambda role (not the state machine role)
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
}
