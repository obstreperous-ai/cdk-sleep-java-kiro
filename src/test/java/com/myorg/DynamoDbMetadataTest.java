package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

public class DynamoDbMetadataTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testDynamoDbTableExists() {
        template.resourceCountIs("AWS::DynamoDB::Table", 1);
    }

    @Test
    public void testDynamoDbTableHasAudioIdPartitionKey() {
        template.hasResourceProperties("AWS::DynamoDB::Table", Match.objectLike(Map.of(
            "KeySchema", List.of(
                Map.of("AttributeName", "audioId", "KeyType", "HASH")
            ),
            "AttributeDefinitions", List.of(
                Map.of("AttributeName", "audioId", "AttributeType", "S")
            )
        )));
    }

    @Test
    public void testDynamoDbTableUsesPayPerRequestBilling() {
        template.hasResourceProperties("AWS::DynamoDB::Table", Match.objectLike(Map.of(
            "BillingMode", "PAY_PER_REQUEST"
        )));
    }

    @Test
    public void testDynamoDbTableHasPointInTimeRecovery() {
        template.hasResourceProperties("AWS::DynamoDB::Table", Match.objectLike(Map.of(
            "PointInTimeRecoverySpecification", Match.objectLike(Map.of(
                "PointInTimeRecoveryEnabled", true
            ))
        )));
    }

    @Test
    public void testDynamoDbTableHasRetainRemovalPolicy() {
        // DeletionPolicy: Retain indicates RemovalPolicy.RETAIN
        template.hasResource("AWS::DynamoDB::Table", Match.objectLike(Map.of(
            "DeletionPolicy", "Retain",
            "UpdateReplacePolicy", "Retain"
        )));
    }

    @Test
    public void testStateMachineDefinitionContainsPutItem() {
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("dynamodb:PutItem"),
            "State machine definition should contain dynamodb:PutItem task");
    }

    @Test
    public void testStateMachineDefinitionContainsUpdateItem() {
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("dynamodb:UpdateItem"),
            "State machine definition should contain dynamodb:UpdateItem task");
    }

    @Test
    public void testStateMachineRoleHasDynamoDbPermissions() {
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:PutItem",
                            "dynamodb:UpdateItem"
                        )),
                        "Effect", "Allow",
                        "Resource", Match.anyValue()
                    ))
                ))
            ))
        )));

        // Verify the Resource is not a wildcard "*" - it should reference the DynamoDB table
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Action", Match.arrayWith(List.of(
                            "dynamodb:PutItem",
                            "dynamodb:UpdateItem"
                        )),
                        "Effect", "Allow",
                        "Resource", Match.not(Match.exact("*"))
                    ))
                ))
            ))
        )));
    }
}
