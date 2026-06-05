package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

public class SnsNotificationTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testTwoSnsTopicsExist() {
        template.resourceCountIs("AWS::SNS::Topic", 2);
    }

    @Test
    public void testSnsTopicsAreEncryptedWithKms() {
        template.hasResourceProperties("AWS::SNS::Topic", Match.objectLike(Map.of(
            "KmsMasterKeyId", Match.anyValue()
        )));
    }

    @Test
    public void testStateMachineDefinitionContainsSnsPublish() {
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("sns:Publish"),
            "State machine definition should contain sns:Publish action");
    }

    @Test
    public void testStateMachineDefinitionContainsCatchBlock() {
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("Catch"),
            "State machine definition should contain Catch block for error handling");
    }

    @Test
    public void testStateMachineDefinitionContainsFailedStatusUpdate() {
        // The error path should update DynamoDB with FAILED status via UpdateItem
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("FAILED") && templateJson.contains("errorInfo"),
            "State machine definition should contain FAILED status and errorInfo on error path");
    }

    @Test
    public void testIamPolicyGrantsSnsPublishToSpecificResources() {
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
}
