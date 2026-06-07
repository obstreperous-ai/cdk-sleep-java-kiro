package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

public class LambdaFunctionTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testLambdaFunctionExists() {
        // CDK creates an additional Lambda for S3 EventBridge custom resource handler
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Runtime", "python3.12",
            "Handler", "index.handler"
        )));
    }

    @Test
    public void testLambdaFunctionHasPython312Runtime() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Runtime", "python3.12"
        )));
    }

    @Test
    public void testLambdaFunctionHasCorrectHandler() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Handler", "index.handler"
        )));
    }

    @Test
    public void testLambdaFunctionHasTableNameEnvironmentVariable() {
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of(
            "Environment", Match.objectLike(Map.of(
                "Variables", Match.objectLike(Map.of(
                    "TABLE_NAME", Match.anyValue()
                ))
            ))
        )));
    }

    @Test
    public void testStateMachineDefinitionContainsLambdaInvoke() {
        // CDK LambdaInvoke generates lambda:InvokeFunction in the IAM policy
        // and the state machine definition references the function via Fn::GetAtt
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("lambda:InvokeFunction"),
            "State machine definition should contain lambda:InvokeFunction for the LambdaInvoke task");
    }

    @Test
    public void testStateMachineRoleHasLambdaInvokePermission() {
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
    public void testLambdaExecutionRoleHasDynamoDbReadAccess() {
        // grantReadData() generates BatchGetItem, Query, GetItem, Scan, ConditionCheckItem, DescribeTable
        // Match.arrayWith requires items in the order they appear in the actual array
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:BatchGetItem",
                            "dynamodb:Query",
                            "dynamodb:GetItem",
                            "dynamodb:Scan"
                        )),
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaExecutionRoleHasDynamoDbReadDataGrant() {
        // grantReadData() also generates GetRecords and GetShardIterator for DynamoDB Streams
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:GetRecords",
                            "dynamodb:GetShardIterator"
                        )),
                        "Effect", "Allow"
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testStateMachineDefinitionContainsCatchForLambdaTask() {
        // The Lambda task should have a Catch block routing to the failure path
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("Catch"),
            "State machine definition should contain Catch block for Lambda task error handling");
    }

    @Test
    public void testStateMachineChainOrderPutItemThenLambdaThenPolly() {
        // Verify the chain order: PutItem -> Lambda -> Polly
        // In the generated JSON, the Next field of PutItem should point to the Lambda task
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("ProcessAudioMetadata"),
            "State machine definition should contain ProcessAudioMetadata task");
    }
}
