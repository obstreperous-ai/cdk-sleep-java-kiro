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
    public void testInputBucketHasEventBridgeEnabled() {
        // The input bucket is distinguished from the output bucket by having
        // EventBridge notifications enabled. CDK implements this via a
        // Custom::S3BucketNotifications resource with EventBridgeConfiguration.
        template.resourceCountIs("Custom::S3BucketNotifications", 1);
        template.hasResourceProperties("Custom::S3BucketNotifications", Match.objectLike(Map.of(
            "BucketName", Match.anyValue(),
            "NotificationConfiguration", Match.objectLike(Map.of(
                "EventBridgeConfiguration", Match.objectLike(Map.of())
            ))
        )));
    }

    @Test
    public void testBothBucketsHaveSecurityProperties() {
        // Verify there are exactly 2 S3 buckets
        template.resourceCountIs("AWS::S3::Bucket", 2);

        // Verify at least one bucket has encryption, versioning, and public access block.
        // Both buckets share these security properties.
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
    public void testEventBridgeRuleExists() {
        // Verify an EventBridge rule exists that matches S3 Object Created events
        // and includes a detail filter scoped to a specific bucket
        template.hasResourceProperties("AWS::Events::Rule", Match.objectLike(Map.of(
            "EventPattern", Match.objectLike(Map.of(
                "source", List.of("aws.s3"),
                "detail-type", List.of("Object Created"),
                "detail", Match.objectLike(Map.of(
                    "bucket", Match.objectLike(Map.of(
                        "name", Match.anyValue()
                    ))
                ))
            )),
            "State", "ENABLED"
        )));
    }

    @Test
    public void testBucketPoliciesEnforceSsl() {
        // enforceSsl(true) on both buckets generates BucketPolicy resources
        // with a Deny statement for non-SSL requests (aws:SecureTransport=false)
        template.resourceCountIs("AWS::S3::BucketPolicy", 2);

        template.hasResourceProperties("AWS::S3::BucketPolicy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "Effect", "Deny",
                        "Condition", Match.objectLike(Map.of(
                            "Bool", Match.objectLike(Map.of(
                                "aws:SecureTransport", "false"
                            ))
                        ))
                    ))
                ))
            ))
        )));
    }
}
