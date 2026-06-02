package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

public class CdkBaseTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testStackSynthesizes() {
        assertNotNull(template);
    }

    @Test
    public void testInputBucketExists() {
        // Verify an S3 bucket with encryption, versioning, public access block,
        // and EventBridge notifications enabled
        template.hasResourceProperties("AWS::S3::Bucket", Map.of(
            "BucketEncryption", Map.of(
                "ServerSideEncryptionConfiguration", List.of(
                    Map.of("ServerSideEncryptionByDefault", Map.of(
                        "SSEAlgorithm", "AES256"
                    ))
                )
            ),
            "VersioningConfiguration", Map.of(
                "Status", "Enabled"
            ),
            "PublicAccessBlockConfiguration", Map.of(
                "BlockPublicAcls", true,
                "BlockPublicPolicy", true,
                "IgnorePublicAcls", true,
                "RestrictPublicBuckets", true
            )
        ));
    }

    @Test
    public void testOutputBucketExists() {
        // Verify there are exactly 2 S3 buckets (input and output)
        template.resourceCountIs("AWS::S3::Bucket", 2);
    }

    @Test
    public void testEventBridgeRuleExists() {
        // Verify an EventBridge rule exists that matches S3 Object Created events
        template.hasResourceProperties("AWS::Events::Rule", Map.of(
            "EventPattern", Map.of(
                "source", List.of("aws.s3"),
                "detail-type", List.of("Object Created")
            ),
            "State", "ENABLED"
        ));
    }

    @Test
    public void testInputBucketHasEventBridgeEnabled() {
        // When eventBridgeEnabled(true) is set on a CDK Bucket, it creates
        // a custom resource that enables EventBridge notifications.
        // This manifests as a Custom::S3BucketNotifications resource in the template.
        template.resourceCountIs("Custom::S3BucketNotifications", 1);
    }
}
