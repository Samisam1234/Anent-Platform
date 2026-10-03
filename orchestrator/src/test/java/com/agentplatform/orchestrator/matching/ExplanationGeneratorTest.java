package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for career-track compatibility in generated explanations.
 *
 * <p>The classifier gained EMBEDDED, VLSI_FPGA and AI_ML, but this generator still switched
 * over only the original four values with a {@code default -> throw}. The result was that
 * every match against a listing in one of the new disciplines failed with
 * {@code IllegalArgumentException: Unknown career track} — six tests in
 * {@code JobMatchingServiceTest}, all reaching it through
 * {@code JobMatchingService.evaluateJob}. These tests exercise that exact call for every
 * track the enum can hold, so the same omission cannot recur silently.</p>
 */
@DisplayName("ExplanationGenerator — career-track compatibility")
class ExplanationGeneratorTest {

    private final ExplanationGenerator generator = new ExplanationGenerator();

    private static Job job() {
        return new Job("j-1", "Test Engineer", "Acme", "Hyderabad, India",
                "A test listing.", List.of("Java"), List.of("Docker"),
                "1-3 years", "Full-time", "2026-09-01", "TEST_SOURCE",
                "https://acme.example.com/jobs/j-1", "PUBLIC_API", Instant.now());
    }

    private String generateFor(CareerTrack track) {
        return generator.generate(null, job(), 80, RecommendationLevel.fromScore(80),
                List.of("Java"), List.of("Docker"), true, ExperienceMatchLevel.STRONG_MATCH, track);
    }

    @Test
    @DisplayName("every CareerTrack value produces an explanation without throwing")
    void everyTrackGeneratesAnExplanation() {
        for (CareerTrack track : CareerTrack.values()) {
            String explanation = assertDoesNotThrow(() -> generateFor(track),
                    "ExplanationGenerator must handle " + track + " — it previously threw");
            assertNotNull(explanation);
            assertFalse(explanation.isBlank());
            assertTrue(explanation.contains(track.displayName()),
                    track + " must be named in the explanation, was: " + explanation);
        }
    }

    @Test
    @DisplayName("every CareerTrack value has a non-blank display name")
    void everyTrackHasADisplayName() {
        for (CareerTrack track : CareerTrack.values()) {
            String name = track.displayName();
            assertNotNull(name, track + " has no display name");
            assertFalse(name.isBlank(), track + " has a blank display name");
        }
    }

    @Test
    @DisplayName("the original four tracks keep their established wording")
    void legacyWordingIsPreserved() {
        assertEquals("Software Engineering", CareerTrack.SOFTWARE.displayName());
        assertEquals("Hardware / ECE / VLSI", CareerTrack.HARDWARE.displayName());
        assertEquals("Cross-disciplinary Software & Hardware", CareerTrack.MIXED.displayName());
        assertEquals("Engineering", CareerTrack.UNKNOWN.displayName());
    }

    @Test
    @DisplayName("the three newer tracks are named distinctly, not folded into hardware")
    void newerTracksAreNamedDistinctly() {
        assertEquals("Embedded Systems", CareerTrack.EMBEDDED.displayName());
        assertEquals("VLSI / FPGA", CareerTrack.VLSI_FPGA.displayName());
        assertEquals("AI / ML", CareerTrack.AI_ML.displayName());

        assertFalse(CareerTrack.EMBEDDED.displayName().equals(CareerTrack.HARDWARE.displayName()));
        assertFalse(CareerTrack.VLSI_FPGA.displayName().equals(CareerTrack.HARDWARE.displayName()));
        assertFalse(CareerTrack.AI_ML.displayName().equals(CareerTrack.SOFTWARE.displayName()));
    }

    @Test
    @DisplayName("a null career track degrades safely instead of throwing")
    void nullTrackIsHandledSafely() {
        String explanation = assertDoesNotThrow(() -> generator.generate(null, job(), 80,
                RecommendationLevel.fromScore(80), List.of("Java"), List.of(), true,
                ExperienceMatchLevel.STRONG_MATCH, null));

        assertTrue(explanation.contains(CareerTrack.UNKNOWN.displayName()),
                "a null track must fall back to the neutral wording, was: " + explanation);
    }

    @Test
    @DisplayName("the display-name switch is exhaustive: no value falls through unhandled")
    void displayNameCoversEveryValue() {
        // Guards the invariant the fix depends on: displayName() has no default branch, so
        // this asserts at runtime what the compiler now enforces at build time.
        assertEquals(7, CareerTrack.values().length,
                "if a track was added, add its display name and update this count");
        for (CareerTrack track : CareerTrack.values()) {
            assertDoesNotThrow(track::displayName, "unhandled track: " + track);
        }
    }
}
