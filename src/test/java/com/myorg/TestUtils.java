package com.myorg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awscdk.assertions.Template;

/**
 * Shared test utility methods for the Sleep Audio Processing Pipeline test suite.
 * Centralizes common template parsing logic to avoid duplication across test files.
 */
public final class TestUtils {

    private TestUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts and parses the state machine DefinitionString from the synthesized template.
     * The DefinitionString uses Fn::Join with intrinsic references (Ref, Fn::GetAtt) that
     * appear as non-text parts. These are replaced with placeholder strings so the JSON
     * can be parsed to inspect state machine structure.
     *
     * @param template the CDK assertions Template to extract the state machine from
     * @return the parsed JsonNode representing the state machine definition
     * @throws Exception if the template cannot be parsed or no state machine is found
     */
    public static JsonNode getStateMachineDefinition(Template template) throws Exception {
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
                            // Intrinsic function reference (Ref, Fn::GetAtt) - these appear
                            // inside JSON string values, so insert a plain placeholder string
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
