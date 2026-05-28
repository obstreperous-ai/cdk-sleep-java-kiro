package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CdkBaseTest {

    @Test
    public void testStackSynthesizes() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");

        Template template = Template.fromStack(stack);

        assertNotNull(template);
    }
}
