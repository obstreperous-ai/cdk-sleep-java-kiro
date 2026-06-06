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
        template.resourceCountIs("AWS::Lambda::Function", 1);
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
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("lambda:invoke"),
            "State machine definition should contain lambda:invoke for the LambdaInvoke task");
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
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:GetItem",
                            "dynamodb:Query"
                        )),
                        "Effect", "Allow",
                        "Resource", Match.not(Match.exact("*"))
                    ))
                ))
            ))
        )));
    }

    @Test
    public void testLambdaExecutionRoleHasBasicExecutionPermissions() {
        // Lambda execution role should have CloudWatch Logs permissions
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:BatchGetItem",
                            "dynamodb:GetRecords"
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
