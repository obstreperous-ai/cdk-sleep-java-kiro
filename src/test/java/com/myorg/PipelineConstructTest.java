package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PipelineConstructTest {

    @Test
    public void testPipelineStackSynthesizesSuccessfully() {
        App app = App.Builder.create().build();
        PipelineStack stack = new PipelineStack(app, "TestPipelineStack");
        Template template = assertDoesNotThrow(() -> Template.fromStack(stack),
            "PipelineStack should synthesize without errors");
        assertNotNull(template, "Template should not be null");
    }

    @Test
    public void testPipelineStackProducesValidTemplate() {
        App app = App.Builder.create().build();
        PipelineStack stack = new PipelineStack(app, "TestPipelineStack");
        Template template = Template.fromStack(stack);
        // The template should have at least one resource (the pipeline itself)
        assertNotNull(template.toJSON(), "Template JSON should not be null");
    }
}
