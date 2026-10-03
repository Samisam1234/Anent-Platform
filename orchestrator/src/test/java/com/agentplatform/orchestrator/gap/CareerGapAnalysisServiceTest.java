package com.agentplatform.orchestrator.gap;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 Step 4.1 — deterministic career-gap analysis.
 *
 * <p>Uses the real {@link SkillMatchingEngine} and {@link CareerTrackEngine}
 * (pure, deterministic, no I/O) so matching reuse is exercised end to end. All
 * fixtures are hermetic; no LLM, DB or network.</p>
 */
@DisplayName("CareerGapAnalysisService — deterministic skill gap analysis (Step 4.1)")
class CareerGapAnalysisServiceTest {

    private final CareerGapAnalysisService service =
            new CareerGapAnalysisService(new SkillMatchingEngine(), new CareerTrackEngine());

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private CandidateProfile candidate(String name, List<String> software, List<String> hardware,
                                       List<String> experience,
                                       List<ResumeEvidence> evidence,
                                       List<CareerTrackEvidence> trackEvidence) {
        return new CandidateProfile(name, null, null, null, List.of(),
                software, experience, List.of(), List.of(), List.of(),
                software, hardware, List.of(), List.of(),
                evidence, trackEvidence);
    }

    private CandidateProfile candidate(List<String> software) {
        return candidate("Alice", software, List.of(), List.of(), List.of(), List.of());
    }

    private Job job(String title, String description, List<String> required,
                    List<String> preferred, String experienceRequirement) {
        return new Job("j1", title, "Acme", "Remote", description,
                required, preferred, experienceRequirement, "FULL_TIME",
                "2026-08-25", "MOCK_SOURCE", null, "MOCK", null);
    }

    private List<String> onlyTitles(List<SkillGap> gaps) {
        return gaps.stream().map(SkillGap::canonicalSkill).toList();
    }

    // ─── 1–3. Required skill matching ───────────────────────────────────────

    @Nested
    @DisplayName("Required skill gaps")
    class RequiredTests {

        @Test
        @DisplayName("all required skills matched → no missing required, NO_GAP")
        void allRequiredMatched() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Spring Boot", "PostgreSQL")),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "1-3 years"));

            assertTrue(a.missingRequiredSkills().isEmpty());
            assertEquals(
                    List.of("Java", "Spring Boot", "PostgreSQL"),
                    onlyTitles(a.matchedRequiredSkills()));
            assertEquals(GapSeverity.NO_GAP, a.overallGapSeverity());
        }

        @Test
        @DisplayName("one missing required skill is reported")
        void oneMissingRequired() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "PostgreSQL")),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "1-3 years"));

            assertEquals(List.of("Spring Boot"), onlyTitles(a.missingRequiredSkills()));
            assertEquals(GapSeverity.LOW, a.overallGapSeverity());
        }

        @Test
        @DisplayName("multiple missing required skills are reported and add weight")
        void multipleMissingRequired() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of()),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "1-3 years"));

            assertEquals(List.of("Java", "Spring Boot", "PostgreSQL"),
                    onlyTitles(a.missingRequiredSkills()));
            // 3 required × 3 = 9 → HIGH
            assertEquals(GapSeverity.HIGH, a.overallGapSeverity());
        }
    }

    // ─── 4–5. Preferred vs required distinction ─────────────────────────────

    @Nested
    @DisplayName("Required vs preferred distinction")
    class RequiredPreferredTests {

        @Test
        @DisplayName("no required gaps; preferred gaps reported separately (Step 4 example)")
        void section4Example() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Spring Boot", "PostgreSQL")),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"),
                            List.of("Docker", "Microservices"), "1-3 years"));

            assertEquals(List.of(), onlyTitles(a.missingRequiredSkills()));
            assertEquals(List.of("Docker", "Microservices"), onlyTitles(a.missingPreferredSkills()));
            assertTrue(a.missingRequiredSkills().isEmpty());
        }

        @Test
        @DisplayName("preferred-only gaps yield LOW (never reported as blocking required)")
        void preferredOnlyGaps() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Spring Boot")),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot"),
                            List.of("Docker", "Microservices"), "1-3 years"));

            assertTrue(a.missingRequiredSkills().isEmpty());
            assertEquals(2, a.missingPreferredSkills().size());
            // 2 preferred × 1 = 2 → LOW (not treated as a required blocker)
            assertEquals(GapSeverity.LOW, a.overallGapSeverity());
        }

        @Test
        @DisplayName("matched preferred skills are tracked separately, not as gaps")
        void matchedPreferredReported() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Docker")),
                    job("Java Developer", "backend",
                            List.of("Java"),
                            List.of("Docker", "Microservices"), "1-3 years"));

            assertEquals(List.of("Docker"), onlyTitles(a.matchedPreferredSkills()));
            assertEquals(List.of("Microservices"), onlyTitles(a.missingPreferredSkills()));
        }
    }

    // ─── 6–8. Canonicalization, duplicates, unknown skills ─────────────────

    @Nested
    @DisplayName("Canonicalization and robustness")
    class CanonicalTests {

        @Test
        @DisplayName("canonical aliases match (Postgres ↔ PostgreSQL)")
        void canonicalAliases() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("PostgreSQL")),
                    job("Java Developer", "backend",
                            List.of("Postgres"), List.of(), "1-3 years"));

            assertTrue(a.missingRequiredSkills().isEmpty());
            assertEquals(List.of("PostgreSQL"), onlyTitles(a.matchedRequiredSkills()));
        }

        @Test
        @DisplayName("duplicate job skills are de-duplicated in the gap output")
        void duplicateSkills() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of()),
                    job("Developer", "backend",
                            List.of("Java", "Java", "Spring Boot"), List.of(), "1-3 years"));

            assertTrue(a.missingRequiredSkills().contains(
                    new SkillGap("Java", List.of())));
            assertEquals(2, a.missingRequiredSkills().size());
            assertEquals(List.of("Java", "Spring Boot"), onlyTitles(a.missingRequiredSkills()));
        }

        @Test
        @DisplayName("unknown candidate skills → all job requirements missing, high severity")
        void unknownCandidateSkills() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of()),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL", "Docker"),
                            List.of(), "3-5 years"));

            assertEquals(4, a.missingRequiredSkills().size());
            assertEquals(GapSeverity.CRITICAL, a.overallGapSeverity());
        }
    }

    // ─── 9–10. Experience gap ───────────────────────────────────────────────

    @Nested
    @DisplayName("Experience gap")
    class ExperienceTests {

        @Test
        @DisplayName("structured both sides → numeric gap (job 2y, candidate 1y → 1)")
        void structuredGap() {
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of("Java"), List.of(), List.of("1 year at Acme"), List.of(), List.of()),
                    job("Java Developer", "backend", List.of("Java"), List.of(), "2 years"));

            ExperienceGap gap = a.experienceGap();
            assertTrue(gap.knowable());
            assertEquals(2, gap.requiredYears());
            assertEquals(1, gap.candidateYears());
            assertEquals(1, gap.gapYears());
            assertTrue(gap.hasShortfall());
        }

        @Test
        @DisplayName("entry-level job → zero required years, no numeric shortfall")
        void entryLevelJobNoShortfall() {
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of("Java"), List.of(), List.of(), List.of(), List.of()),
                    job("Java Intern", "backend", List.of("Java"), List.of(), "Fresher / 0-1 years"));

            ExperienceGap gap = a.experienceGap();
            assertTrue(gap.knowable());
            assertEquals(0, gap.requiredYears());
            assertEquals(0, gap.gapYears());
            assertFalse(gap.hasShortfall());
        }

        @Test
        @DisplayName("unknown candidate experience → experience gap UNKNOWN, never invented")
        void unknownExperience() {
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of("Java"), List.of(), List.of(), List.of(), List.of()),
                    job("Senior Java Developer", "backend", List.of("Java"), List.of(), "5+ years"));

            ExperienceGap gap = a.experienceGap();
            assertFalse(gap.knowable());
            assertNull(gap.gapYears());
            assertFalse(gap.hasShortfall());
        }

        @Test
        @DisplayName("candidate not exceeding requirement → no shortfall even when knowable")
        void sufficientExperience() {
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of("Java"), List.of(), List.of("3 years at Acme"), List.of(), List.of()),
                    job("Java Developer", "backend", List.of("Java"), List.of(), "2 years"));

            ExperienceGap gap = a.experienceGap();
            assertTrue(gap.knowable());
            assertEquals(0, gap.gapYears());
            assertFalse(gap.hasShortfall());
        }

        @Test
        @DisplayName("range requirement uses the upper bound (2-4 years → required 4)")
        void rangeRequirementUsesUpperBound() {
            assertEquals(4, CareerGapAnalysisService.parseJobRequiredYears("2-4 years"));
        }
    }

    // ─── 11–12. Career-track comparison ─────────────────────────────────────

    @Nested
    @DisplayName("Career track")
    class TrackTests {

        @Test
        @DisplayName("candidate software track matches a software job → no mismatch")
        void matchingTrack() {
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of("Java"), List.of(), List.of(),
                            List.of(),
                            List.of(new CareerTrackEvidence("Software Engineering", 4.0, List.of("Java")))),
                    job("Senior Java Developer", "microservices backend API",
                            List.of("Java"), List.of(), "5+ years"));

            assertEquals(CareerTrack.SOFTWARE, a.candidateTrack());
            assertEquals(CareerTrack.SOFTWARE, a.jobTrack());
            assertFalse(a.trackMismatch());
        }

        @Test
        @DisplayName("candidate software track vs deterministically hardware job → mismatch")
        void mismatchedTrack() {
            CareerGapAnalysis a = service.analyze(
                    candidate("Bob", List.of("Java"), List.of(),
                            List.of(),
                            List.of(),
                            List.of(new CareerTrackEvidence("Software Engineering", 4.0, List.of("Java")))),
                    job("Verilog RTL Design Engineer", "FPGA verification UVM",
                            List.of("Verilog"), List.of(), "3-5 years"));

            assertEquals(CareerTrack.SOFTWARE, a.candidateTrack());
            // A Verilog/RTL/UVM role is now classified as VLSI_FPGA rather than generic
            // HARDWARE; it is still a hardware-family mismatch against a software profile.
            assertEquals(CareerTrack.VLSI_FPGA, a.jobTrack());
            assertTrue(a.trackMismatch());
        }

        @Test
        @DisplayName("job track UNKNOWN when not deterministically determinable → no mismatch guessed")
        void unknownJobTrack() {
            // Empty title/description/skills → CareerTrackEngine.classifyJob finds no
            // software or hardware signals → UNKNOWN (never guessed from stray text).
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of(), List.of(), List.of(), List.of(), List.of()),
                    new Job("generic", "", null, "Remote", null,
                            null, null, null, null, null, "MOCK_SOURCE", null, "MOCK", null));

            assertEquals(CareerTrack.UNKNOWN, a.jobTrack());
            assertFalse(a.trackMismatch());
        }
    }

    // ─── 13. Severity formula ───────────────────────────────────────────────

    @Nested
    @DisplayName("Severity calculation")
    class SeverityTests {

        @Test
        @DisplayName("scoring formula thresholds are transparent")
        void formulaThresholds() {
            assertEquals(GapSeverity.NO_GAP,
                    CareerGapAnalysisService.severity(0, 0, false, false));
            assertEquals(GapSeverity.LOW,
                    CareerGapAnalysisService.severity(1, 0, false, false));
            assertEquals(GapSeverity.LOW,
                    CareerGapAnalysisService.severity(0, 3, false, false));
            assertEquals(GapSeverity.MEDIUM,
                    CareerGapAnalysisService.severity(2, 0, false, false));
            assertEquals(GapSeverity.HIGH,
                    CareerGapAnalysisService.severity(3, 0, false, false));
            assertEquals(GapSeverity.CRITICAL,
                    CareerGapAnalysisService.severity(4, 0, false, false));
        }

        @Test
        @DisplayName("required gaps outrank preferred gaps at equal counts")
        void requiredOutweighsPreferred() {
            assertEquals(GapSeverity.HIGH,
                    CareerGapAnalysisService.severity(3, 0, false, false));
            assertEquals(GapSeverity.LOW,
                    CareerGapAnalysisService.severity(0, 3, false, false));
        }

        @Test
        @DisplayName("experience shortfall and track mismatch add modest weight")
        void addedComponents() {
            // 2 required (6) + experience (2) = 8 → HIGH; + track mismatch (2) = 10 → CRITICAL
            assertEquals(GapSeverity.HIGH,
                    CareerGapAnalysisService.severity(2, 0, true, false));
            assertEquals(GapSeverity.CRITICAL,
                    CareerGapAnalysisService.severity(2, 0, true, true));
        }
    }

    // ─── 14. Priority ordering ──────────────────────────────────────────────

    @Nested
    @DisplayName("Improvement priorities")
    class PriorityTests {

        @Test
        @DisplayName("required skills first (alphabetical), then experience, then preferred, 1-based ranks")
        void priorityOrdering() {
            Job javaJob = job("Java Developer", "backend",
                    List.of("Spring Boot", "Java"), List.of("Kubernetes", "Docker"), "3 years");
            CareerGapAnalysis a = service.analyze(
                    candidate("Alice", List.of(), List.of(), List.of("1 year at Acme"), List.of(), List.of()),
                    javaJob);

            List<String> descriptions = a.improvementPriorities().stream()
                    .map(ImprovementPriority::description).toList();
            assertEquals(List.of(
                    "Java is required by the target job but is not present in the candidate profile.",
                    "Spring Boot is required by the target job but is not present in the candidate profile.",
                    "Target role requires 3 years of experience; candidate has 1 year.",
                    "Docker is preferred by the target job but is currently missing.",
                    "kubernetes is preferred by the target job but is currently missing."
            ), descriptions);

            assertEquals(1, a.improvementPriorities().get(0).rank());
            assertEquals(5, a.improvementPriorities().get(4).rank());
        }

        @Test
        @DisplayName("no gaps → no priorities")
        void noPrioritiesWhenNoGaps() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java", "Spring Boot", "PostgreSQL")),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "1-3 years"));

            assertTrue(a.improvementPriorities().isEmpty());
        }
    }

    // ─── 15. Evidence / explainability ─────────────────────────────────────

    @Nested
    @DisplayName("Evidence / explainability")
    class EvidenceTests {

        @Test
        @DisplayName("matched skill carries resume sections as evidence")
        void matchedSkillHasEvidence() {
            CandidateProfile c = candidate("Alice",
                    List.of("Spring Boot"), List.of(), List.of(),
                    List.of(ResumeEvidence.of("Spring Boot", ResumeEvidence.SourceSection.PROJECT,
                                    "built a Spring Boot service", ResumeEvidence.EvidenceStrength.STRONG),
                            ResumeEvidence.of("Java", ResumeEvidence.SourceSection.SKILLS,
                                    "Java listed", ResumeEvidence.EvidenceStrength.MEDIUM)),
                    List.of());

            CareerGapAnalysis a = service.analyze(
                    c, job("Java Developer", "backend", List.of("Java", "Spring Boot"), List.of(), "1-3 years"));

            SkillGap boot = a.matchedRequiredSkills().stream()
                    .filter(s -> s.canonicalSkill().equals("Spring Boot")).findFirst().orElseThrow();
            assertEquals(List.of(ResumeEvidence.SourceSection.PROJECT), boot.evidenceSources());
        }

        @Test
        @DisplayName("missing skill carries no fabricated evidence")
        void missingSkillNoEvidence() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of("Java")),
                    job("Java Developer", "backend", List.of("Java", "Docker"), List.of(), "1-3 years"));

            SkillGap docker = a.missingRequiredSkills().stream()
                    .filter(s -> s.canonicalSkill().equals("Docker")).findFirst().orElseThrow();
            assertTrue(docker.evidenceSources().isEmpty());
        }
    }

    // ─── 16–18. Edge cases ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty candidate & job → no fabricated gaps, NO_GAP, no priorities")
        void emptyProfileAndJob() {
            CareerGapAnalysis a = service.analyze(candidate(List.of()), job("", "", List.of(), List.of(), null));

            assertTrue(a.missingRequiredSkills().isEmpty());
            assertTrue(a.missingPreferredSkills().isEmpty());
            assertEquals(GapSeverity.NO_GAP, a.overallGapSeverity());
            assertTrue(a.improvementPriorities().isEmpty());
            assertNull(a.experienceGap().gapYears());
        }

        @Test
        @DisplayName("empty candidate vs demanding job → real job requirements listed, never invented")
        void emptyCandidateVsDemandingJob() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of()),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot"), List.of("Docker"), "2 years"));

            assertEquals(List.of("Java", "Spring Boot"), onlyTitles(a.missingRequiredSkills()));
            assertEquals(List.of("Docker"), onlyTitles(a.missingPreferredSkills()));
            // Gaps stem only from the job's own declared requirements.
        }

        @Test
        @DisplayName("null candidate and null job handled without crashing")
        void nullInputsHandled() {
            CareerGapAnalysis a = service.analyze(null, null);

            assertEquals(GapSeverity.NO_GAP, a.overallGapSeverity());
            assertTrue(a.missingRequiredSkills().isEmpty());
            assertNull(a.jobId());
        }

        @Test
        @DisplayName("job with null required/preferred → no gaps reported")
        void emptyJobData() {
            CareerGapAnalysis a = service.analyze(
                    candidate(List.of()),
                    new Job("j", "Role", "Co", "Remote", "d", null, null,
                            null, null, null, "MOCK_SOURCE", null, "MOCK", null));

            assertTrue(a.missingRequiredSkills().isEmpty());
            assertTrue(a.missingPreferredSkills().isEmpty());
        }
    }
}