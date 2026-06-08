package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

public class MultiEnvironmentTest {

    @Test
    public void testStackSynthesizesWithDevEnvironment() {
        App app = App.Builder.create().build();
        app.getNode().setContext("environment", "dev");
        CdkBaseStack stack = new CdkBaseStack(app, "DevStack");
        Template template = Template.fromStack(stack);
        assertNotNull(template, "Stack should synthesize successfully with dev environment");
    }

    @Test
    public void testStackSynthesizesWithProdEnvironment() {
        App app = App.Builder.create().build();
        app.getNode().setContext("environment", "prod");
        CdkBaseStack stack = new CdkBaseStack(app, "ProdStack");
        Template template = Template.fromStack(stack);
        assertNotNull(template, "Stack should synthesize successfully with prod environment");
    }

    @Test
    public void testEnvironmentTagAppliedForDev() {
        App app = App.Builder.create().build();
        app.getNode().setContext("environment", "dev");
        CdkBaseStack stack = new CdkBaseStack(app, "DevStack");
        Template template = Template.fromStack(stack);

        // Verify that the Environment tag is applied to resources
        // CDK applies stack-level tags to all taggable resources
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "Tags", Match.arrayWith(java.util.List.of(
                Match.objectLike(Map.of(
                    "Key", "Environment",
                    "Value", "dev"
                ))
            ))
        )));
    }

    @Test
    public void testEnvironmentTagAppliedForProd() {
        App app = App.Builder.create().build();
        app.getNode().setContext("environment", "prod");
        CdkBaseStack stack = new CdkBaseStack(app, "ProdStack");
        Template template = Template.fromStack(stack);

        // Verify that the Environment tag is applied to resources
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "Tags", Match.arrayWith(java.util.List.of(
                Match.objectLike(Map.of(
                    "Key", "Environment",
                    "Value", "prod"
                ))
            ))
        )));
    }

    @Test
    public void testDefaultEnvironmentIsDevWhenNoContextProvided() {
        App app = App.Builder.create().build();
        CdkBaseStack stack = new CdkBaseStack(app, "DefaultStack");
        Template template = Template.fromStack(stack);

        // With no context, environment defaults to "dev"
        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "Tags", Match.arrayWith(java.util.List.of(
                Match.objectLike(Map.of(
                    "Key", "Environment",
                    "Value", "dev"
                ))
            ))
        )));
    }
}
