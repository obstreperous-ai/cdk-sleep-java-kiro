package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class AdvancedErrorHandlingTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testLambdaTaskRetryIncludesAWSLambdaException() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode lambdaState = states.get("ProcessAudioMetadata");
        assertNotNull(lambdaState, "ProcessAudioMetadata state should exist");

        JsonNode retriers = lambdaState.get("Retry");
        assertNotNull(retriers, "ProcessAudioMetadata should have Retry configuration");

        boolean hasAWSLambdaException = false;
        for (JsonNode retrier : retriers) {
            JsonNode errors = retrier.get("ErrorEquals");
            for (JsonNode error : errors) {
                if ("Lambda.AWSLambdaException".equals(error.asText())) {
                    hasAWSLambdaException = true;
                    break;
                }
            }
            if (hasAWSLambdaException) break;
        }
        assertTrue(hasAWSLambdaException,
            "Lambda retry should include Lambda.AWSLambdaException");
    }

    @Test
    public void testLambdaTaskHasGranularCatchBlock() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode lambdaState = states.get("ProcessAudioMetadata");
        JsonNode catchers = lambdaState.get("Catch");
        assertNotNull(catchers, "ProcessAudioMetadata should have Catch configuration");

        // Should have at least 2 catch blocks: specific errors + States.ALL
        assertTrue(catchers.size() >= 2,
            "ProcessAudioMetadata should have at least 2 Catch blocks (granular + States.ALL)");

        // First catch should be for specific Lambda errors (evaluated first)
        JsonNode firstCatch = catchers.get(0);
        JsonNode firstErrors = firstCatch.get("ErrorEquals");
        boolean hasLambdaServiceException = false;
        boolean hasLambdaAWSException = false;
        for (JsonNode error : firstErrors) {
            if ("Lambda.ServiceException".equals(error.asText())) {
                hasLambdaServiceException = true;
            }
            if ("Lambda.AWSLambdaException".equals(error.asText())) {
                hasLambdaAWSException = true;
            }
        }
        assertTrue(hasLambdaServiceException,
            "First catch should include Lambda.ServiceException");
        assertTrue(hasLambdaAWSException,
            "First catch should include Lambda.AWSLambdaException");
    }

    @Test
    public void testGranularCatchRoutesToFailurePath() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        // Lambda granular catch routes to UpdateMetadataStatusFailed
        JsonNode lambdaState = states.get("ProcessAudioMetadata");
        JsonNode lambdaCatchers = lambdaState.get("Catch");
        assertEquals("UpdateMetadataStatusFailed", lambdaCatchers.get(0).get("Next").asText(),
            "Lambda granular catch should route to UpdateMetadataStatusFailed");
    }

    @Test
    public void testGranularCatchHasResultPath() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        // Lambda granular catch uses $.error resultPath
        JsonNode lambdaState = states.get("ProcessAudioMetadata");
        JsonNode lambdaCatchers = lambdaState.get("Catch");
        assertEquals("$.error", lambdaCatchers.get(0).get("ResultPath").asText(),
            "Lambda granular catch should use $.error resultPath");
    }

    @Test
    public void testUpdateMetadataStatusHasCatchBlock() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        JsonNode updateState = states.get("UpdateMetadataStatus");
        assertNotNull(updateState, "UpdateMetadataStatus state should exist");

        JsonNode catchers = updateState.get("Catch");
        assertNotNull(catchers, "UpdateMetadataStatus should have Catch configuration");

        boolean routesToFailure = false;
        for (JsonNode catcher : catchers) {
            if ("UpdateMetadataStatusFailed".equals(catcher.get("Next").asText())) {
                routesToFailure = true;
                break;
            }
        }
        assertTrue(routesToFailure,
            "UpdateMetadataStatus Catch should route to UpdateMetadataStatusFailed");
    }

    @Test
    public void testSuccessPublishHasCatchBlock() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");

        JsonNode publishState = states.get("PublishSuccessNotification");
        assertNotNull(publishState, "PublishSuccessNotification state should exist");

        JsonNode catchers = publishState.get("Catch");
        assertNotNull(catchers, "PublishSuccessNotification should have Catch configuration");

        boolean routesToFailure = false;
        for (JsonNode catcher : catchers) {
            if ("UpdateMetadataStatusFailed".equals(catcher.get("Next").asText())) {
                routesToFailure = true;
                break;
            }
        }
        assertTrue(routesToFailure,
            "PublishSuccessNotification Catch should route to UpdateMetadataStatusFailed");
    }

    /**
     * Delegates to shared TestUtils for state machine definition extraction.
     */
    private JsonNode getStateMachineDefinition() throws Exception {
        return TestUtils.getStateMachineDefinition(template);
    }

    @Test
    public void testFailurePathDynamoDbUpdateItemParameters() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode updateFailed = states.get("UpdateMetadataStatusFailed");
        assertNotNull(updateFailed, "UpdateMetadataStatusFailed state should exist");

        JsonNode parameters = updateFailed.get("Parameters");
        assertNotNull(parameters, "UpdateMetadataStatusFailed should have Parameters");

        JsonNode expressionAttrValues = parameters.get("ExpressionAttributeValues");
        assertNotNull(expressionAttrValues,
            "UpdateMetadataStatusFailed Parameters should have ExpressionAttributeValues");

        // Verify :status is set to FAILED
        JsonNode statusValue = expressionAttrValues.get(":status");
        assertNotNull(statusValue, "ExpressionAttributeValues should have :status");
        assertEquals("FAILED", statusValue.get("S").asText(),
            ":status should be set to 'FAILED'");

        // Verify :errorInfo references $.error.Cause
        JsonNode errorInfoValue = expressionAttrValues.get(":errorInfo");
        assertNotNull(errorInfoValue, "ExpressionAttributeValues should have :errorInfo");
        assertEquals("$.error.Cause", errorInfoValue.get("S.$").asText(),
            ":errorInfo should reference '$.error.Cause'");
    }

    @Test
    public void testPutMetadataRecordRetryConfiguration() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode putMetadata = states.get("PutMetadataRecord");
        assertNotNull(putMetadata, "PutMetadataRecord state should exist");

        JsonNode retriers = putMetadata.get("Retry");
        assertNotNull(retriers, "PutMetadataRecord should have Retry configuration");

        // Find the single retrier that contains all 3 DynamoDB/timeout error types
        boolean hasProvisionedThroughputExceeded = false;
        boolean hasInternalServerError = false;
        boolean hasStatesTimeout = false;
        int maxAttempts = 0;
        boolean foundCombinedRetrier = false;

        for (JsonNode retrier : retriers) {
            JsonNode errors = retrier.get("ErrorEquals");
            boolean retrierHasProvisioned = false;
            boolean retrierHasInternal = false;
            boolean retrierHasTimeout = false;

            for (JsonNode error : errors) {
                String errorText = error.asText();
                if ("DynamoDB.ProvisionedThroughputExceededException".equals(errorText)) {
                    retrierHasProvisioned = true;
                }
                if ("DynamoDB.InternalServerError".equals(errorText)) {
                    retrierHasInternal = true;
                }
                if ("States.Timeout".equals(errorText)) {
                    retrierHasTimeout = true;
                }
            }

            if (retrierHasProvisioned && retrierHasInternal && retrierHasTimeout) {
                foundCombinedRetrier = true;
                hasProvisionedThroughputExceeded = true;
                hasInternalServerError = true;
                hasStatesTimeout = true;
                JsonNode maxAttemptsNode = retrier.get("MaxAttempts");
                assertNotNull(maxAttemptsNode,
                    "Combined retrier should have MaxAttempts defined");
                maxAttempts = maxAttemptsNode.asInt();
                break;
            }

            // Track individual error presence in case they are split
            if (retrierHasProvisioned) hasProvisionedThroughputExceeded = true;
            if (retrierHasInternal) hasInternalServerError = true;
            if (retrierHasTimeout) hasStatesTimeout = true;
        }

        assertTrue(foundCombinedRetrier,
            "PutMetadataRecord should have a single retrier containing all 3 error types");
        assertTrue(hasProvisionedThroughputExceeded,
            "PutMetadataRecord retry should include DynamoDB.ProvisionedThroughputExceededException");
        assertTrue(hasInternalServerError,
            "PutMetadataRecord retry should include DynamoDB.InternalServerError");
        assertTrue(hasStatesTimeout,
            "PutMetadataRecord retry should include States.Timeout");
        assertEquals(3, maxAttempts,
            "PutMetadataRecord retry should have MaxAttempts=3");
    }
}
