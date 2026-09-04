package com.agentplatform.ui.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Structured result parsed from the Gemini model's JSON output.
 *
 * <p>Which fields are populated depends on the {@link TaskType}: every type
 * fills only the fields its system prompt requires, leaving the others
 * {@code null}.</p>
 */
public record StructuredAgentResult(
        String summary,
        List<String> keyPoints,
        String category,
        Double confidence
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Parses the model's raw output into a {@link StructuredAgentResult}.
     *
     * <p>Tolerates the model wrapping its answer in {@code ```json ... ```}
     * fences or surrounding whitespace.</p>
     *
     * @param modelOutput the raw AI response text
     * @return the parsed structured result
     * @throws IllegalArgumentException if no JSON object can be extracted
     */
    public static StructuredAgentResult fromJson(String modelOutput) {
        String json = extractJsonObject(modelOutput);
        try {
            return MAPPER.readValue(json, StructuredAgentResult.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("AI model output is not valid structured JSON", e);
        }
    }

    private static String extractJsonObject(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            throw new IllegalArgumentException("AI model output is empty");
        }

        // Strip ```json / ``` code fences, then locate the outermost {...} block.
        String text = modelOutput
                .replaceAll("(?s)```(?:json)?", "")
                .trim();

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI model output contains no JSON object");
        }
        return text.substring(start, end + 1);
    }
}