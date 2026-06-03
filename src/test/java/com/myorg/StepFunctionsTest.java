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
    public void testStateMachineDefinitionContainsPollyTask() {
        // Verify the state machine definition references Polly synthesizeSpeech
        // The DefinitionString is a Fn::Join intrinsic, so we check the raw template JSON
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("polly"),
            "State machine definition should reference polly service");
        assertTrue(templateJson.contains("synthesizeSpeech"),
            "State machine definition should reference synthesizeSpeech action");
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
    public void testStateMachineRoleHasPollyPermissions() {
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
