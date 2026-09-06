package com.agentplatform.orchestrator.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the controlled {@link ApplicationRecommendation} enum.
 */
@DisplayName("ApplicationRecommendation — controlled values only")
class ApplicationRecommendationTest {

    private static final List<String> EXPECTED_VALUES = List.of(
            "STRONGLY_RECOMMENDED",
            "RECOMMENDED",
            "APPLY_WITH_IMPROVEMENTS",
            "LOW_PRIORITY",
            "NOT_RECOMMENDED"
    );

    @Test
    @DisplayName("enum contains exactly the five controlled values")
    void controlledValuesOnly() {
        ApplicationRecommendation[] values = ApplicationRecommendation.values();
        assertEquals(5, values.length, "Must have exactly 5 controlled values");

        for (ApplicationRecommendation v : values) {
            assertTrue(EXPECTED_VALUES.contains(v.name()),
                    "Unexpected enum value: " + v.name());
        }
    }

    @Test
    @DisplayName("values are in the expected order")
    void expectedOrder() {
        ApplicationRecommendation[] values = ApplicationRecommendation.values();
        for (int i = 0; i < EXPECTED_VALUES.size(); i++) {
            assertEquals(EXPECTED_VALUES.get(i), values[i].name(),
                    "Mismatch at index " + i);
        }
    }

    @Test
    @DisplayName("no duplicate or missing values")
    void noDuplicatesOrMissing() {
        ApplicationRecommendation[] values = ApplicationRecommendation.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertEquals(false, values[i] == values[j],
                        "Duplicate enum constant detected");
            }
        }
    }
}

