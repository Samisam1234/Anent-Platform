package com.agentplatform.orchestrator.gap;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.CareerTrackEvidence;
import com.agentplatform.orchestrator.resume.ResumeEvidence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 Step 4.2 — deterministic improvement &amp; learning priorities.
 *
 * <p>Builds on the Step 4.1 {@link CareerGapAnalysis} (no gap calculation is
 * duplicated). Verifies the expanded {@link ImprovementPriority} model: explanatory,
 * factual descriptions plus the structured learning-readiness fields
 * {@code focus / type / reason}, deterministic ordering and rank consistency.</p>
 */
@DisplayName("Improvement & learning priorities (Step 4.2)")
class ImprovementPriorityTest {

    private final CareerGapAnalysisService service =
            new CareerGapAnalysisService(new SkillMatchingEngine(), new CareerTrackEngine());

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private CandidateProfile candidate(List<String> software, List<String> experience) {
        return new CandidateProfile("Alice", null, null, null, List.of(),
                software, experience, List.of(), List.of(), List.of(),
                software, List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private Job job(String title, List<String> required, List<String> preferred,
                    String experienceRequirement) {
        return new Job("j1", title, "Acme", "Remote", "backend",
                required, preferred, experienceRequirement, "FULL_TIME",
                "2026-08-25", "MOCK_SOURCE", null, "MOCK", null);
    }

    // ─── 1. No gaps ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No gaps")
    class NoGapTests {

        @Test
        @DisplayName("no missing skills, no experience gap → NO_GAP and empty priorities")
        void noGaps() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Spring Boot", "PostgreSQL"), List.of("3 years at Acme")),
                    job("Java Developer", List.of("Java", "Spring Boot", "PostgreSQL"),
                            List.of(), "2 years"));

            assertEquals(GapSeverity.NO_GAP, a.overallGapSeverity());
            assertTrue(a.improvementPriorities().isEmpty());
            assertTrue(a.missingRequiredSkills().isEmpty());
            assertTrue(a.missingPreferredSkills().isEmpty());
            assertTrue(!a.experienceGap().hasShortfall());
        }
    }

    // ─── 2. Required skill priorities ───────────────────────────────────────

    @Nested
    @DisplayName("Required skill priorities")
    class RequiredPriorityTests {

        @Test
        @DisplayName("each missing required skill exposes focus, type REQUIRED_SKILL, reason and explanation")
        void requiredSkillFields() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("PostgreSQL"), List.of()),
                    job("Java Developer", List.of("Java", "PostgreSQL"), List.of(), "1-3 years"));

            assertEquals(1, a.improvementPriorities().size());
            ImprovementPriority p = a.improvementPriorities().get(0);
            assertEquals("Java", p.focus());
            assertEquals(ImprovementPriority.TYPE_REQUIRED_SKILL, p.type());
            assertEquals("required by target job", p.reason());
            assertEquals("Java is required by the target job but is not present in the candidate profile.",
                    p.description());
            assertEquals(1, p.rank());
        }
    }

    // ─── 3. Preferred skill priorities ──────────────────────────────────────

    @Nested
    @DisplayName("Preferred skill priorities")
    class PreferredPriorityTests {

        @Test
        @DisplayName("missing preferred skills expose type PREFERRED_SKILL and prefer explanation")
        void preferredSkillFields() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java"), List.of()),
                    job("Java Developer", List.of("Java"), List.of("Docker", "Microservices"), "1-3 years"));

            assertEquals(2, a.improvementPriorities().size());
            ImprovementPriority p = a.improvementPriorities().get(0);
            assertEquals("Docker", p.focus());
            assertEquals(ImprovementPriority.TYPE_PREFERRED_SKILL, p.type());
            assertEquals("preferred by target job", p.reason());
            assertEquals("Docker is preferred by the target job but is currently missing.",
                    p.description());
        }
    }

    // ─── 4 & 6. Ordering ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Priority ordering")
    class OrderingTests {

        @Test
        @DisplayName("required first (alphabetical), then experience, then preferred")
        void requiredExperiencePreferredOrdering() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of(), List.of("1 year at Acme")),
                    job("Java Developer",
                            List.of("Spring Boot", "Java"),
                            List.of("Kubernetes", "Docker"),
                            "3 years"));

            List<ImprovementPriority> ps = a.improvementPriorities();
            assertEquals(List.of(
                    ImprovementPriority.TYPE_REQUIRED_SKILL,
                    ImprovementPriority.TYPE_REQUIRED_SKILL,
                    ImprovementPriority.TYPE_EXPERIENCE,
                    ImprovementPriority.TYPE_PREFERRED_SKILL,
                    ImprovementPriority.TYPE_PREFERRED_SKILL
            ), ps.stream().map(ImprovementPriority::type).toList());

            assertEquals(List.of("Java", "Spring Boot"),
                    ps.stream().filter(p -> ImprovementPriority.TYPE_REQUIRED_SKILL.equals(p.type()))
                            .map(ImprovementPriority::focus).toList());
            assertEquals(List.of("Docker", "kubernetes"),
                    ps.stream().filter(p -> ImprovementPriority.TYPE_PREFERRED_SKILL.equals(p.type()))
                            .map(ImprovementPriority::focus).toList());
        }
    }

    // ─── 5. Experience-only priority ────────────────────────────────────────

    @Nested
    @DisplayName("Experience-only priority")
    class ExperiencePriorityTests {

        @Test
        @DisplayName("experience shortfall becomes its own priority with structured values")
        void experienceOnly() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java"), List.of("1 year at Acme")),
                    job("Java Developer", List.of("Java"), List.of(), "2 years"));

            // required Java matched → only the experience priority remains.
            assertEquals(1, a.improvementPriorities().size());
            ImprovementPriority p = a.improvementPriorities().get(0);
            assertEquals(ImprovementPriority.TYPE_EXPERIENCE, p.type());
            assertEquals("Experience", p.focus());
            assertEquals("experience shortfall", p.reason());
            assertEquals("Target role requires 2 years of experience; candidate has 1 year.",
                    p.description());
        }
    }

    // ─── 7. Canonical alphabetical ordering ─────────────────────────────────

    @Nested
    @DisplayName("Alphabetical canonical ordering")
    class AlphabeticalTests {

        @Test
        @DisplayName("missing required skills are ordered by canonical name, not input order")
        void canonicalAlphabetical() {
            // Job lists requirements in deliberately non-alphabetical order.
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of(), List.of()),
                    job("Developer",
                            List.of("Kubernetes", "Docker", "Java", "PostgreSQL"),
                            List.of(), "1-3 years"));

            List<String> canonicals = a.improvementPriorities().stream()
                    .map(ImprovementPriority::focus).toList();
            // Natural ordering: uppercase-leading terms before lowercase 'kubernetes'.
            assertEquals(List.of("Docker", "Java", "PostgreSQL", "kubernetes"), canonicals);
        }
    }

    // ─── 8. Consecutive ranks ───────────────────────────────────────────────

    @Nested
    @DisplayName("Consecutive ranks")
    class RankTests {

        @Test
        @DisplayName("ranks are 1-based and consecutive, no duplicates")
        void consecutiveRanks() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of(), List.of("1 year at Acme")),
                    job("Java Developer",
                            List.of("Spring Boot", "Java"),
                            List.of("Kubernetes", "Docker"),
                            "3 years"));

            List<Integer> ranks = a.improvementPriorities().stream()
                    .map(ImprovementPriority::rank).toList();
            assertEquals(List.of(1, 2, 3, 4, 5), ranks);
            assertEquals(5, ranks.stream().distinct().count());
        }
    }

    // ─── 9 & 15. Deterministic descriptions / repeated analysis ─────────────

    @Nested
    @DisplayName("Determinism")
    class DeterminismTests {

        @Test
        @DisplayName("repeated analysis of identical input yields identical priorities")
        void deterministicRepeated() {
            CandidateProfile c = candidate(List.of("PostgreSQL"), List.of("1 year at Acme"));
            Job j = job("Java Developer",
                    List.of("Java", "PostgreSQL"), List.of("Docker"), "2 years");

            List<ImprovementPriority> first = service.analyze(c, j).improvementPriorities();
            for (int i = 0; i < 20; i++) {
                assertEquals(first, service.analyze(c, j).improvementPriorities());
            }
        }

        @Test
        @DisplayName("no timestamps, uuids or randomness appear in priority output")
        void noNondeterministicTokens() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of(), List.of("1 year at Acme")),
                    job("Java Developer", List.of("Java"), List.of("Docker"), "2 years"));

            for (ImprovementPriority p : a.improvementPriorities()) {
                String blob = p.focus() + p.type() + p.reason() + p.description();
                assertTrue(!blob.matches("(?i).*\\d{4}-\\d{2}-\\d{2}.*")); // no dates
                assertTrue(!blob.matches("(?i).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}.*")); // no uuid
                assertTrue(blob.equals(blob.trim()));
            }
        }
    }

    // ─── 10. Experience unknown ─────────────────────────────────────────────

    @Nested
    @DisplayName("Unknown experience")
    class UnknownExperienceTests {

        @Test
        @DisplayName("no structured candidate experience → no experience priority created")
        void unknownExperienceNoPriority() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java"), List.of()),
                    job("Senior Java Developer", List.of("Java"), List.of(), "5+ years"));

            // candidate experience empty → experience is unknown → no EXPERIENCE priority.
            assertTrue(a.improvementPriorities().stream()
                    .noneMatch(p -> ImprovementPriority.TYPE_EXPERIENCE.equals(p.type())));
            assertTrue(!a.experienceGap().hasShortfall());
        }
    }

    // ─── 11. Severity / priority consistency ────────────────────────────────

    @Nested
    @DisplayName("Severity / priority consistency")
    class ConsistencyTests {

        @Test
        @DisplayName("NO_GAP produces no priorities; severity never fabricates a priority")
        void noSeverityDrivenFakePriorities() {
            // Track mismatch alone (severity contribution) must NOT create a priority.
            CandidateProfile sw = candidate(List.of("Java"), List.of("3 years at Acme"));
            CareerGapAnalysis a = service.analyze(
                    sw, job("Verilog RTL Design Engineer",
                            List.of("Verilog"), List.of(), "2 years"));

            assertTrue(a.trackMismatch());
            assertTrue(a.overallGapSeverity() != GapSeverity.NO_GAP);
            // Priorities correspond only to the single missing required skill (Verilog).
            assertEquals(1, a.improvementPriorities().size());
            assertEquals("Verilog", a.improvementPriorities().get(0).focus());
        }
    }

    // ─── 12. Complete match ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Complete match")
    class CompleteMatchTests {

        @Test
        @DisplayName("candidate satisfies every requirement → NO_GAP, empty priorities")
        void completeMatch() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                            List.of("4 years at Acme")),
                    job("Java Developer",
                            List.of("Java", "Spring Boot", "PostgreSQL"),
                            List.of("Docker"),
                            "3 years"));

            assertEquals(GapSeverity.NO_GAP, a.overallGapSeverity());
            assertTrue(a.missingRequiredSkills().isEmpty());
            assertTrue(a.missingPreferredSkills().isEmpty());
            assertTrue(a.improvementPriorities().isEmpty());
        }
    }

    // ─── 13. Duplicate skills ───────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate skills")
    class DuplicateTests {

        @Test
        @DisplayName("duplicate job requirements yield a single priority per canonical skill")
        void duplicatesDedup() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of(), List.of()),
                    job("Developer", List.of("Java", "Java", "Spring Boot"), List.of(), "1-3 years"));

            assertEquals(2, a.improvementPriorities().size());
            assertEquals(List.of("Java", "Spring Boot"),
                    a.improvementPriorities().stream().map(ImprovementPriority::focus).toList());
        }
    }

    // ─── 14. Canonical aliases ──────────────────────────────────────────────

    @Nested
    @DisplayName("Canonical aliases")
    class AliasTests {

        @Test
        @DisplayName("alias (Postgres) resolves to canonical (PostgreSQL) in the priority")
        void aliasCanonical() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of(), List.of()),
                    job("Developer", List.of("Postgres"), List.of(), "1-3 years"));

            assertEquals(1, a.improvementPriorities().size());
            assertEquals("PostgreSQL", a.improvementPriorities().get(0).focus());
        }

        @Test
        @DisplayName("candidate alias satisfies the requirement → no priority")
        void candidateAliasSatisfies() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("PostgreSQL"), List.of()),
                    job("Developer", List.of("Postgres"), List.of(), "1-3 years"));

            assertTrue(a.improvementPriorities().isEmpty());
        }
    }
}