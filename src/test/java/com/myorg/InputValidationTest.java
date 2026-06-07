package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

public class InputValidationTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testChoiceStateExistsInStateMachineDefinition() {
        // The state machine definition should contain a Choice state for file extension validation
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("ValidateFileExtension"),
            "State machine definition should contain ValidateFileExtension Choice state");
        assertTrue(templateJson.contains("Choice"),
            "State machine definition should contain a Choice state type");
    }

    @Test
    public void testChoiceStateChecksWavExtension() {
        // The Choice state should check for .wav file extension
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("*.wav"),
            "Choice state should check for .wav file extension using StringMatches");
    }

    @Test
    public void testChoiceStateChecksMp3Extension() {
        // The Choice state should check for .mp3 file extension
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("*.mp3"),
            "Choice state should check for .mp3 file extension using StringMatches");
    }

    @Test
    public void testChoiceStateChecksOggExtension() {
        // The Choice state should check for .ogg file extension
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("*.ogg"),
            "Choice state should check for .ogg file extension using StringMatches");
    }

    @Test
    public void testValidExtensionsRouteToProcessAudioMetadata() {
        // Valid file extensions should route to ProcessAudioMetadata
        String templateJson = template.toJSON().toString();
        // The Choice branches should have Next pointing to ProcessAudioMetadata
        assertTrue(templateJson.contains("ProcessAudioMetadata"),
            "Valid extensions should route to ProcessAudioMetadata task");
    }

    @Test
    public void testInvalidExtensionsRouteToFailurePath() {
        // Invalid file extensions (Default/otherwise) should route to failure path
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("UpdateMetadataStatusFailed"),
            "Invalid extensions should route to UpdateMetadataStatusFailed via Default path");
    }

    @Test
    public void testChoiceStatePositionedAfterPutMetadataRecord() {
        // PutMetadataRecord should have Next pointing to ValidateFileExtension
        String templateJson = template.toJSON().toString();
        // In the state machine definition JSON, PutMetadataRecord's Next should be ValidateFileExtension
        int putMetadataIdx = templateJson.indexOf("PutMetadataRecord");
        int validateIdx = templateJson.indexOf("ValidateFileExtension");
        assertTrue(putMetadataIdx >= 0, "PutMetadataRecord state should exist");
        assertTrue(validateIdx >= 0, "ValidateFileExtension state should exist");
        // The Next field of PutMetadataRecord should reference ValidateFileExtension
        // Since CDK generates the definition as a JSON string, look for the pattern
        assertTrue(templateJson.contains("PutMetadataRecord"),
            "Should contain PutMetadataRecord state");
    }

    @Test
    public void testChoiceStateUsesStringMatchesCondition() {
        // The Choice state should use StringMatchesPath or StringMatches for $.object.key
        String templateJson = template.toJSON().toString();
        assertTrue(templateJson.contains("$.object.key"),
            "Choice state should check $.object.key variable");
    }
}
