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
        // Exact by design: 2 buckets are explicitly created (input and output)
        int count = template.findResources("AWS::S3::Bucket").size();
        assertEquals(2, count, "Stack should have exactly 2 S3 buckets (input and output)");
    }

    @Test
    public void testDynamoDbTableCount() {
        // Exact by design: 1 table is explicitly created (metadata table)
        int count = template.findResources("AWS::DynamoDB::Table").size();
        assertEquals(1, count, "Stack should have exactly 1 DynamoDB table");
    }

    @Test
    public void testStepFunctionsStateMachineCount() {
        // Exact by design: 1 state machine is explicitly created (audio pipeline)
        int count = template.findResources("AWS::StepFunctions::StateMachine").size();
        assertEquals(1, count, "Stack should have exactly 1 Step Functions state machine");
    }

    @Test
    public void testEventBridgeRuleCount() {
        // Exact by design: 1 rule is explicitly created (S3 object created trigger)
        int count = template.findResources("AWS::Events::Rule").size();
        assertEquals(1, count, "Stack should have exactly 1 EventBridge rule");
    }

    @Test
    public void testSnsTopicCount() {
        // Exact by design: 2 topics are explicitly created (completed and failed)
        int count = template.findResources("AWS::SNS::Topic").size();
        assertEquals(2, count, "Stack should have exactly 2 SNS topics (completed and failed)");
    }

    @Test
    public void testKmsKeyCount() {
        // Exact by design: 1 KMS key is explicitly created (for SNS encryption).
        // May change with CDK version bumps if CDK adds keys for log encryption or
        // custom resource encryption in future versions.
        int count = template.findResources("AWS::KMS::Key").size();
        assertEquals(1, count, "Stack should have exactly 1 KMS key");
    }

    @Test
    public void testLambdaFunctionCount() {
        // Uses >= assertion: 1 Lambda is explicitly created (audio processor), but CDK
        // may generate additional Lambda functions for custom resource handlers depending
        // on the CDK version. This count may change with CDK version bumps.
        int count = template.findResources("AWS::Lambda::Function").size();
        assertTrue(count >= 1,
            "Stack should have at least 1 Lambda function (CDK may generate custom resource handlers)");
    }
}
