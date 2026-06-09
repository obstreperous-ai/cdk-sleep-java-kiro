package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AdvancedErrorHandlingTest {

    private Template template;

    @BeforeEach
    public void setUp() {
        App app = new App();
        CdkBaseStack stack = new CdkBaseStack(app, "test");
        template = Template.fromStack(stack);
    }

    @Test
    public void testPollyTaskHasRetryPolicy() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode pollyState = states.get("SynthesizeSpeech");
        assertNotNull(pollyState, "SynthesizeSpeech state should exist");

        JsonNode retriers = pollyState.get("Retry");
        assertNotNull(retriers, "SynthesizeSpeech should have Retry configuration");
        assertTrue(retriers.isArray() && retriers.size() > 0,
            "SynthesizeSpeech should have at least one retry policy");

        // Check for Polly-specific errors in the retry config
        boolean hasPollyServiceException = false;
        boolean hasPollyThrottling = false;
        for (JsonNode retrier : retriers) {
            JsonNode errors = retrier.get("ErrorEquals");
            for (JsonNode error : errors) {
                if ("Polly.ServiceException".equals(error.asText())) {
                    hasPollyServiceException = true;
                }
                if ("Polly.ThrottlingException".equals(error.asText())) {
                    hasPollyThrottling = true;
                }
            }
        }
        assertTrue(hasPollyServiceException,
            "Polly retry should include Polly.ServiceException");
        assertTrue(hasPollyThrottling,
            "Polly retry should include Polly.ThrottlingException");
    }

    @Test
    public void testPollyRetryHasBackoffConfig() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode pollyState = states.get("SynthesizeSpeech");
        JsonNode retriers = pollyState.get("Retry");

        boolean foundConfig = false;
        for (JsonNode retrier : retriers) {
            JsonNode errors = retrier.get("ErrorEquals");
            for (JsonNode error : errors) {
                if ("Polly.ServiceException".equals(error.asText())) {
                    assertEquals(3, retrier.get("MaxAttempts").asInt(),
                        "Polly retry maxAttempts should be 3");
                    assertEquals(2, retrier.get("IntervalSeconds").asInt(),
                        "Polly retry interval should be 2 seconds");
                    assertEquals(2.0, retrier.get("BackoffRate").asDouble(), 0.01,
                        "Polly retry backoff rate should be 2.0");
                    foundConfig = true;
                    break;
                }
            }
            if (foundConfig) break;
        }
        assertTrue(foundConfig, "Should find Polly retry config with backoff parameters");
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
    public void testPollyTaskHasGranularCatchBlock() throws Exception {
        JsonNode definition = getStateMachineDefinition();
        JsonNode states = definition.get("States");
        JsonNode pollyState = states.get("SynthesizeSpeech");
        JsonNode catchers = pollyState.get("Catch");
        assertNotNull(catchers, "SynthesizeSpeech should have Catch configuration");

        // Should have at least 2 catch blocks: specific errors + States.ALL
        assertTrue(catchers.size() >= 2,
            "SynthesizeSpeech should have at least 2 Catch blocks (granular + States.ALL)");

        // First catch should be for specific Polly errors (evaluated first)
        JsonNode firstCatch = catchers.get(0);
        JsonNode firstErrors = firstCatch.get("ErrorEquals");
        boolean hasPollyServiceException = false;
        boolean hasPollyThrottling = false;
        for (JsonNode error : firstErrors) {
            if ("Polly.ServiceException".equals(error.asText())) {
                hasPollyServiceException = true;
            }
            if ("Polly.ThrottlingException".equals(error.asText())) {
                hasPollyThrottling = true;
            }
        }
        assertTrue(hasPollyServiceException,
            "First catch should include Polly.ServiceException");
        assertTrue(hasPollyThrottling,
            "First catch should include Polly.ThrottlingException");
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

        // Polly granular catch routes to UpdateMetadataStatusFailed
        JsonNode pollyState = states.get("SynthesizeSpeech");
        JsonNode pollyCatchers = pollyState.get("Catch");
        assertEquals("UpdateMetadataStatusFailed", pollyCatchers.get(0).get("Next").asText(),
            "Polly granular catch should route to UpdateMetadataStatusFailed");
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

        // Polly granular catch uses $.error resultPath
        JsonNode pollyState = states.get("SynthesizeSpeech");
        JsonNode pollyCatchers = pollyState.get("Catch");
        assertEquals("$.error", pollyCatchers.get(0).get("ResultPath").asText(),
            "Polly granular catch should use $.error resultPath");
    }

    /**
     * Extracts and parses the state machine DefinitionString from the synthesized template.
     */
    private JsonNode getStateMachineDefinition() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String templateJson = mapper.writeValueAsString(template.toJSON());
        JsonNode root = mapper.readTree(templateJson);
        JsonNode resources = root.get("Resources");
        for (java.util.Iterator<String> it = resources.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            JsonNode resource = resources.get(key);
            if ("AWS::StepFunctions::StateMachine".equals(resource.get("Type").asText())) {
                JsonNode definitionString = resource.get("Properties").get("DefinitionString");
                if (definitionString.has("Fn::Join")) {
                    JsonNode parts = definitionString.get("Fn::Join").get(1);
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (part.isTextual()) {
                            sb.append(part.asText());
                        } else {
                            sb.append("PLACEHOLDER");
                        }
                    }
                    return mapper.readTree(sb.toString());
                } else if (definitionString.isTextual()) {
                    return mapper.readTree(definitionString.asText());
                }
            }
        }
        throw new RuntimeException("StateMachine resource not found in template");
    }
}
