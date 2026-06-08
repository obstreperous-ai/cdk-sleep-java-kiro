package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnapshotTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testS3BucketCount() {
        int count = template.findResources("AWS::S3::Bucket").size();
        assertEquals(2, count, "Stack should have exactly 2 S3 buckets (input and output)");
    }

    @Test
    public void testDynamoDbTableCount() {
        int count = template.findResources("AWS::DynamoDB::Table").size();
        assertEquals(1, count, "Stack should have exactly 1 DynamoDB table");
    }

    @Test
    public void testStepFunctionsStateMachineCount() {
        int count = template.findResources("AWS::StepFunctions::StateMachine").size();
        assertEquals(1, count, "Stack should have exactly 1 Step Functions state machine");
    }

    @Test
    public void testEventBridgeRuleCount() {
        int count = template.findResources("AWS::Events::Rule").size();
        assertEquals(1, count, "Stack should have exactly 1 EventBridge rule");
    }

    @Test
    public void testSnsTopicCount() {
        int count = template.findResources("AWS::SNS::Topic").size();
        assertEquals(2, count, "Stack should have exactly 2 SNS topics (completed and failed)");
    }

    @Test
    public void testKmsKeyCount() {
        int count = template.findResources("AWS::KMS::Key").size();
        assertEquals(1, count, "Stack should have exactly 1 KMS key");
    }

    @Test
    public void testLambdaFunctionCount() {
        int count = template.findResources("AWS::Lambda::Function").size();
        assertTrue(count >= 1,
            "Stack should have at least 1 Lambda function (CDK may generate custom resource handlers)");
    }
}
