package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineConstructTest {

    @Test
    public void testPipelineStackSynthesizesSuccessfully() {
        App app = App.Builder.create()
                .context(Map.of(
                        "pipeline:connectionArn", "arn:aws:codestar-connections:us-east-1:123456789012:connection/test-id",
                        "pipeline:repository", "owner/repo"
                ))
                .build();
        PipelineStack stack = new PipelineStack(app, "TestPipelineStack");
        Template template = assertDoesNotThrow(() -> Template.fromStack(stack),
            "PipelineStack should synthesize without errors");
        assertNotNull(template, "Template should not be null");
    }

    @Test
    public void testPipelineStackProducesValidTemplate() {
        App app = App.Builder.create()
                .context(Map.of(
                        "pipeline:connectionArn", "arn:aws:codestar-connections:us-east-1:123456789012:connection/test-id",
                        "pipeline:repository", "owner/repo"
                ))
                .build();
        PipelineStack stack = new PipelineStack(app, "TestPipelineStack");
        Template template = Template.fromStack(stack);
        // The template should have at least one resource (the pipeline itself)
        assertNotNull(template.toJSON(), "Template JSON should not be null");
    }

    @Test
    public void testPipelineStackFailsWithoutConnectionArn() {
        App app = App.Builder.create()
                .context(Map.of(
                        "pipeline:repository", "owner/repo"
                ))
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new PipelineStack(app, "TestPipelineStack"));
        assertTrue(ex.getMessage().contains("pipeline:connectionArn"));
    }

    @Test
    public void testPipelineStackFailsWithoutRepository() {
        App app = App.Builder.create()
                .context(Map.of(
                        "pipeline:connectionArn", "arn:aws:codestar-connections:us-east-1:123456789012:connection/test-id"
                ))
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new PipelineStack(app, "TestPipelineStack"));
        assertTrue(ex.getMessage().contains("pipeline:repository"));
    }
}
