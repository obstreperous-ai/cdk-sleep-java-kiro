package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class EndToEndFlowTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testHappyPathStateChainOrder() throws Exception {
        // Verify the complete happy-path state chain:
        // PutMetadataRecord -> ValidateFileExtension -> ProcessAudioMetadata -> UpdateMetadataStatus -> PublishSuccessNotification
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        // Step 1: PutMetadataRecord -> ValidateFileExtension
        JsonNode putMetadata = states.get("PutMetadataRecord");
        assertNotNull(putMetadata, "PutMetadataRecord state should exist");
        assertEquals("ValidateFileExtension", putMetadata.get("Next").asText(),
            "PutMetadataRecord should chain to ValidateFileExtension");

        // Step 2: ValidateFileExtension is a Choice state - valid extensions route to ProcessAudioMetadata
        JsonNode validateExt = states.get("ValidateFileExtension");
        assertNotNull(validateExt, "ValidateFileExtension state should exist");
        assertEquals("Choice", validateExt.get("Type").asText(),
            "ValidateFileExtension should be a Choice state");
        // At least one choice rule should route to ProcessAudioMetadata
        JsonNode choices = validateExt.get("Choices");
        assertNotNull(choices, "Choice state should have Choices array");
        boolean routesToProcessAudio = false;
        for (JsonNode choice : choices) {
            if ("ProcessAudioMetadata".equals(choice.get("Next").asText())) {
                routesToProcessAudio = true;
                break;
            }
        }
        assertTrue(routesToProcessAudio,
            "ValidateFileExtension should route valid extensions to ProcessAudioMetadata");

        // Step 3: ProcessAudioMetadata -> UpdateMetadataStatus
        JsonNode processAudio = states.get("ProcessAudioMetadata");
        assertNotNull(processAudio, "ProcessAudioMetadata state should exist");
        assertEquals("UpdateMetadataStatus", processAudio.get("Next").asText(),
            "ProcessAudioMetadata should chain to UpdateMetadataStatus");

        // Step 4: UpdateMetadataStatus -> PublishSuccessNotification
        JsonNode updateStatus = states.get("UpdateMetadataStatus");
        assertNotNull(updateStatus, "UpdateMetadataStatus state should exist");
        assertEquals("PublishSuccessNotification", updateStatus.get("Next").asText(),
            "UpdateMetadataStatus should chain to PublishSuccessNotification");

        // Step 5: PublishSuccessNotification is the terminal state (End=true)
        JsonNode publishSuccess = states.get("PublishSuccessNotification");
        assertNotNull(publishSuccess, "PublishSuccessNotification state should exist");
        assertTrue(publishSuccess.get("End").asBoolean(),
            "PublishSuccessNotification should be the terminal state");
    }

    @Test
    public void testErrorPathChain() throws Exception {
        // Verify the full error path chain:
        // any task Catch -> UpdateMetadataStatusFailed -> PublishFailureNotification -> End
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        // Verify UpdateMetadataStatusFailed -> PublishFailureNotification
        JsonNode updateFailed = states.get("UpdateMetadataStatusFailed");
        assertNotNull(updateFailed, "UpdateMetadataStatusFailed state should exist");
        assertEquals("PublishFailureNotification", updateFailed.get("Next").asText(),
            "UpdateMetadataStatusFailed should chain to PublishFailureNotification");

        // Verify PublishFailureNotification is terminal (End=true)
        JsonNode publishFailure = states.get("PublishFailureNotification");
        assertNotNull(publishFailure, "PublishFailureNotification state should exist");
        assertTrue(publishFailure.get("End").asBoolean(),
            "PublishFailureNotification should be the terminal state");
    }

    @Test
    public void testProcessAudioMetadataCatchRoutesToFailurePath() throws Exception {
        // Verify that ProcessAudioMetadata has a Catch that routes to UpdateMetadataStatusFailed
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode processAudio = states.get("ProcessAudioMetadata");
        assertNotNull(processAudio, "ProcessAudioMetadata state should exist");

        JsonNode catchers = processAudio.get("Catch");
        assertNotNull(catchers, "ProcessAudioMetadata should have Catch configuration");
        boolean routesToFailure = false;
        for (JsonNode catcher : catchers) {
            if ("UpdateMetadataStatusFailed".equals(catcher.get("Next").asText())) {
                routesToFailure = true;
                break;
            }
        }
        assertTrue(routesToFailure,
            "ProcessAudioMetadata Catch should route to UpdateMetadataStatusFailed");
    }

    @Test
    public void testValidationFailureRoutesToErrorPath() throws Exception {
        // Verify that ValidateFileExtension Default routes to ValidationError
        // which then chains to UpdateMetadataStatusFailed
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        JsonNode validateExt = states.get("ValidateFileExtension");
        String defaultTarget = validateExt.get("Default").asText();
        assertEquals("ValidationError", defaultTarget,
            "ValidateFileExtension Default should route to ValidationError");

        JsonNode validationError = states.get("ValidationError");
        assertNotNull(validationError, "ValidationError state should exist");
        assertEquals("UpdateMetadataStatusFailed", validationError.get("Next").asText(),
            "ValidationError should chain to UpdateMetadataStatusFailed");
    }

    /**
     * Delegates to shared TestUtils for state machine definition extraction.
     */
    private JsonNode getStateMachineDefinition() throws Exception {
        return TestUtils.getStateMachineDefinition(template);
    }
}
