package com.agentplatform.orchestrator.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ApplicationAdvisorResponse} contract:
 * readiness score bounds, defensive copies, null safety, determinism.
 */
@DisplayName("ApplicationAdvisorResponse — structured response contract")
class ApplicationAdvisorResponseTest {

    @Test
    @DisplayName("valid response can be created with factory")
    void validResponseFactory() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED,
                75,
                List.of("Java", "Spring"),
                List.of("Missing Kubernetes"),
                List.of("Add Kubernetes to resume")
        );
        assertEquals(ApplicationRecommendation.RECOMMENDED, resp.recommendation());
        assertEquals(75, resp.applicationReadinessScore());
        assertEquals(2, resp.strengths().size());
        assertEquals(1, resp.concerns().size());
        assertEquals(1, resp.recommendedActions().size());
    }

    @Test
    @DisplayName("readiness score lower bound (0)")
    void scoreLowerBound() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.NOT_RECOMMENDED,
                ApplicationAdvisorResponse.MIN_SCORE,
                List.of(), List.of(), List.of());
        assertEquals(0, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("readiness score upper bound (100)")
    void scoreUpperBound() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.STRONGLY_RECOMMENDED,
                ApplicationAdvisorResponse.MAX_SCORE,
                List.of(), List.of(), List.of());
        assertEquals(100, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("score below 0 is clamped to 0")
    void scoreBelowZeroClamped() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.NOT_RECOMMENDED,
                -10,
                List.of(), List.of(), List.of());
        assertEquals(0, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("score above 100 is clamped to 100")
    void scoreAboveHundredClamped() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.STRONGLY_RECOMMENDED,
                150,
                List.of(), List.of(), List.of());
        assertEquals(100, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("foundation factory clamps score")
    void foundationFactoryClampsScore() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.foundation(
                ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS, -5);
        assertEquals(0, resp.applicationReadinessScore());

        resp = ApplicationAdvisorResponse.foundation(
                ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS, 105);
        assertEquals(100, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("strengths list is defensively copied (immutable)")
    void strengthsImmutable() {
        List<String> mutable = Arrays.asList("Java", "Spring");
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED, 75,
                mutable, List.of(), List.of());

        // Original list modification should not affect response
        mutable.set(0, "Mutated");
        assertEquals("Java", resp.strengths().get(0),
                "Response strengths must be independent copy");

        // Response list itself is immutable
        assertThrows(UnsupportedOperationException.class,
                () -> resp.strengths().add("New"));
    }

    @Test
    @DisplayName("concerns list is defensively copied (immutable)")
    void concernsImmutable() {
        List<String> mutable = Arrays.asList("Missing Docker");
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS, 60,
                List.of(), mutable, List.of());

        mutable.set(0, "Mutated");
        assertEquals("Missing Docker", resp.concerns().get(0));

        assertThrows(UnsupportedOperationException.class,
                () -> resp.concerns().add("New"));
    }

    @Test
    @DisplayName("recommendedActions list is defensively copied (immutable)")
    void actionsImmutable() {
        List<String> mutable = Arrays.asList("Learn Docker");
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS, 60,
                List.of(), List.of(), mutable);

        mutable.set(0, "Mutated");
        assertEquals("Learn Docker", resp.recommendedActions().get(0));

        assertThrows(UnsupportedOperationException.class,
                () -> resp.recommendedActions().add("New"));
    }

    @Test
    @DisplayName("null collections become empty lists")
    void nullCollectionsBecomeEmpty() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED, 75,
                null, null, null);

        assertEquals(0, resp.strengths().size());
        assertEquals(0, resp.concerns().size());
        assertEquals(0, resp.recommendedActions().size());

        assertThrows(UnsupportedOperationException.class,
                () -> resp.strengths().add("x"));
    }

    @Test
    @DisplayName("null recommendation defaults to NOT_RECOMMENDED")
    void nullRecommendationDefaults() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                null, 50, List.of(), List.of(), List.of());
        assertEquals(ApplicationRecommendation.NOT_RECOMMENDED, resp.recommendation());
    }

    @Test
    @DisplayName("deterministic repeat behavior")
    void deterministicRepeat() {
        ApplicationAdvisorResponse a = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED, 75,
                List.of("Java"), List.of("Kubernetes"), List.of("Add Kubernetes"));
        ApplicationAdvisorResponse b = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED, 75,
                List.of("Java"), List.of("Kubernetes"), List.of("Add Kubernetes"));

        assertEquals(a.recommendation(), b.recommendation());
        assertEquals(a.applicationReadinessScore(), b.applicationReadinessScore());
        assertEquals(a.strengths(), b.strengths());
        assertEquals(a.concerns(), b.concerns());
        assertEquals(a.recommendedActions(), b.recommendedActions());
    }

    @Test
    @DisplayName("no AgentContext, LLM output, tool arguments, or sensitive data in response")
    void noInternalDataLeakage() {
        ApplicationAdvisorResponse resp = ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED, 75,
                List.of("Java"), List.of("Kubernetes"), List.of("Add Kubernetes"));

        // The response is a record with only safe fields
        // No AgentContext, no raw LLM, no tool args, no resume text, no credentials
        String toString = resp.toString();
        assertTrue(!toString.contains("AgentContext"));
        assertTrue(!toString.contains("LLM"));
        assertTrue(!toString.contains("toolArgument"));
        assertTrue(!toString.contains("password"));
        assertTrue(!toString.contains("credential"));
        assertTrue(!toString.contains("resumeText"));
        assertTrue(!toString.contains("Exception"));
    }
}

