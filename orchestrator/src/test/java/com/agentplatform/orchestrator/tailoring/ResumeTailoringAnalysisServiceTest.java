package com.agentplatform.orchestrator.tailoring;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeEvidence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4.4 — deterministic, explainable ATS resume tailoring analysis.
 *
 * <p>Uses the real {@link CareerGapAnalysisService} (Steps 4.1–4.2) to derive the
 * authoritative gap, then the {@link ResumeTailoringAnalysisService}. All fixtures are
 * hermetic and deterministic — no LLM, database or network. Emphasises truthfulness:
 * the analysis never fabricates skills, experience, projects, or evidence.</p>
 */
@DisplayName("ResumeTailoringAnalysisService — deterministic ATS tailoring (Phase 4.4)")
class ResumeTailoringAnalysisServiceTest {

    private final CareerGapAnalysisService gapService =
            new CareerGapAnalysisService(new SkillMatchingEngine(), new CareerTrackEngine());
    private final ResumeTailoringAnalysisService tailoringService =
            new ResumeTailoringAnalysisService(new CareerTrackEngine());

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private Job job(String title, String description, List<String> required,
                    List<String> preferred, String exp) {
        return new Job("j1", title, "Acme", "Remote", description,
                required, preferred, exp, "FULL_TIME", "2026-08-25", "MOCK_SOURCE",
                null, "MOCK", null);
    }

    private CandidateProfile profile(String name, List<String> software, List<String> hardware,
                                     List<String> skills, List<String> projects,
                                     List<String> experience, List<String> internships,
                                     List<String> certifications, List<String> education,
                                     List<ResumeEvidence> evidence) {
        return new CandidateProfile(name, null, null, null, education, skills,
                experience, internships, projects, certifications, software, hardware,
                List.of(), List.of(), evidence, List.of());
    }

    private CandidateProfile profile(String name, List<String> software, List<String> hardware,
                                     List<String> skills, List<String> projects,
                                     List<String> experience, List<String> internships,
                                     List<String> certifications, List<String> education) {
        return profile(name, software, hardware, skills, projects, experience,
                internships, certifications, education, List.of());
    }

    private CareerGapAnalysis gap(CandidateProfile c, Job j) {
        return gapService.analyze(c, j);
    }

    private ResumeTailoringAnalysis analyze(CandidateProfile c, Job j) {
        return tailoringService.analyze(c, j, gap(c, j));
    }

    private List<String> highlightedNames(ResumeTailoringAnalysis a) {
        return a.highlightedSkills().stream().map(HighlightedSkill::canonicalSkill).toList();
    }

    // ─── 1–3. Required / preferred matching ─────────────────────────────────

    @Nested
    @DisplayName("Matched & missing skills")
    class MatchingTests {

        @Test
        @DisplayName("all required skills matched → none missing, readiness required coverage full")
        void allRequiredMatched() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "2 years"));

            assertEquals(List.of("Java", "Spring Boot", "PostgreSQL"), a.matchedRequiredSkills());
            assertTrue(a.missingRequiredSkills().isEmpty());
        }

        @Test
        @DisplayName("missing required skill is surfaced and clearly labelled")
        void missingRequired() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java", "Docker"), List.of(), "2 years"));

            assertEquals(List.of("Docker"), a.missingRequiredSkills());
            assertTrue(a.missingRequirements().stream()
                    .anyMatch(r -> r.type() == RecommendationType.MISSING_REQUIREMENT
                            && r.focus().equals("Docker")));
        }

        @Test
        @DisplayName("missing preferred skill is surfaced at lower emphasis")
        void missingPreferred() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java"), List.of("AWS", "Kubernetes"), "2 years"));

            assertEquals(List.of("aws", "kubernetes"), a.missingPreferredSkills());
            assertEquals(List.of("aws", "kubernetes"),
                    a.missingRequirements().stream()
                            .filter(r -> r.type() == RecommendationType.MISSING_REQUIREMENT)
                            .map(TailoringRecommendation::focus).toList());
        }
    }

    // ─── 4. Canonicalization ────────────────────────────────────────────────

    @Nested
    @DisplayName("Canonicalization")
    class CanonicalTests {

        @Test
        @DisplayName("Postgres alias resolves to canonical PostgreSQL")
        void canonicalAlias() {
            CandidateProfile c = profile("Alice",
                    List.of("PostgreSQL"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Developer", "backend", List.of("Postgres"), List.of(), "2 years"));

            assertTrue(a.matchedRequiredSkills().contains("PostgreSQL"));
            assertFalse(a.matchedRequiredSkills().contains("Postgres"));
        }
    }

    // ─── 5–7. Highlighted skills & evidence ─────────────────────────────────

    @Nested
    @DisplayName("Highlighted skills and evidence")
    class HighlightTests {

        private ResumeTailoringAnalysis swMatchAnalysis() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "Spring Boot"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            return analyze(c, job("Java Developer", "backend",
                    List.of("Java", "Spring Boot"), List.of("Docker"), "2 years"));
        }

        @Test
        @DisplayName("missing skills never appear among highlighted skills")
        void missingNeverHighlighted() {
            ResumeTailoringAnalysis a = swMatchAnalysis();
            // Docker is missing; Java / Spring Boot are matched.
            assertTrue(a.missingPreferredSkills().contains("Docker"));
            assertFalse(highlightedNames(a).contains("Docker"));
            assertTrue(highlightedNames(a).contains("Java"));
        }

        @Test
        @DisplayName("verified matched skills are highlighted")
        void matchedHighlighted() {
            ResumeTailoringAnalysis a = swMatchAnalysis();
            assertEquals(List.of("Java", "Spring Boot"), highlightedNames(a));
        }

        @Test
        @DisplayName("highlighted skills carry ResumeEvidence when available; evidence never fabricated")
        void evidenceUsedAndNotFabricated() {
            CandidateProfile c = profile("Alice",
                    List.of("Spring Boot", "Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(ResumeEvidence.of("Spring Boot", ResumeEvidence.SourceSection.PROJECT,
                            "built a Spring Boot service", ResumeEvidence.EvidenceStrength.STRONG)));
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot"), List.of(), "2 years"));

            HighlightedSkill boot = a.highlightedSkills().stream()
                    .filter(h -> h.canonicalSkill().equals("Spring Boot")).findFirst().orElseThrow();
            assertEquals(List.of(ResumeEvidence.SourceSection.PROJECT), boot.evidenceSources());

            // Java had no evidence → its evidence list is empty, never fabricated.
            HighlightedSkill javaH = a.highlightedSkills().stream()
                    .filter(h -> h.canonicalSkill().equals("Java")).findFirst().orElseThrow();
            assertTrue(javaH.evidenceSources().isEmpty());

            // A missing requirement has no evidence.
            assertTrue(a.missingRequirements().stream().allMatch(r -> r.evidenceSources().isEmpty()));
        }
    }

    // ─── 8–9. Project relevance ─────────────────────────────────────────────

    @Nested
    @DisplayName("Project relevance")
    class ProjectTests {

        @Test
        @DisplayName("project whose text matches job skills is highlighted")
        void relevantProjectHighlighted() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "Spring Boot"), List.of(), List.of(),
                    List.of("Hospital Management System using Java, Spring Boot, PostgreSQL"),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot"), List.of(), "2 years"));

            assertEquals(1, a.relevantProjects().size());
            assertEquals("Hospital Management System using Java, Spring Boot, PostgreSQL",
                    a.relevantProjects().get(0).content());
            assertTrue(a.relevantProjects().get(0).matchedJobSkills().contains("Java"));
            assertTrue(a.relevantProjects().get(0).matchedJobSkills().contains("Spring Boot"));
        }

        @Test
        @DisplayName("unrelated project is not highlighted")
        void unrelatedProjectNotHighlighted() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of("University library management"),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java"), List.of(), "2 years"));

            assertTrue(a.relevantProjects().isEmpty());
            assertTrue(a.tailoringRecommendations().stream()
                    .noneMatch(r -> r.type() == RecommendationType.HIGHLIGHT_PROJECT));
        }
    }

    // ─── 10–11. Internship relevance ────────────────────────────────────────

    @Nested
    @DisplayName("Internship relevance")
    class InternshipTests {

        @Test
        @DisplayName("relevant internship is highlighted")
        void relevantInternshipHighlighted() {
            CandidateProfile c = profile("Bob",
                    List.of(), List.of("FPGA"), List.of(),
                    List.of(), List.of(), List.of("FPGA design intern at Gravton (Xilinx)"),
                    List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("VLSI Design Engineer", "vlsi rtl verification verilog",
                            List.of("Verilog"), List.of("FPGA"), "2 years"));

            assertFalse(a.relevantInternships().isEmpty());
            assertTrue(a.relevantInternships().stream()
                    .anyMatch(e -> e.content().contains("Gravton")));
            assertTrue(a.tailoringRecommendations().stream()
                    .anyMatch(r -> r.type() == RecommendationType.HIGHLIGHT_INTERNSHIP));
        }

        @Test
        @DisplayName("hardware internship is not falsely represented as software for a software job")
        void hardwareInternshipNotSoftware() {
            CandidateProfile c = profile("Bob",
                    List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of("PCB layout intern at Gravton (hardware)"),
                    List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java"), List.of(), "2 years"));

            assertTrue(a.relevantInternships().isEmpty());
            assertTrue(a.tailoringRecommendations().stream()
                    .noneMatch(r -> r.type() == RecommendationType.HIGHLIGHT_INTERNSHIP));
            // Nothing claims software-engineering experience from the hardware internship.
            assertTrue(a.relevantExperience().isEmpty());
        }
    }

    // ─── 12–16. Section ordering ────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic section ordering")
    class OrderingTests {

        private CandidateProfile swProfile() {
            return profile("Alice",
                    List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), List.of(),
                    List.of("Hospital System using Java, Spring Boot"),
                    List.of("1 year backend developer with Spring Boot"),
                    List.of("Intern writing REST APIs"),
                    List.of("Oracle Java"),
                    List.of("B.Tech CSE"), List.of());
        }

        @Test
        @DisplayName("software job ordering is deterministic and evidence-based")
        void softwareOrdering() {
            ResumeTailoringAnalysis a = analyze(swProfile(),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "2 years"));

            assertEquals(List.of(
                    ResumeSection.SUMMARY, ResumeSection.SKILLS,
                    ResumeSection.PROJECTS, ResumeSection.EXPERIENCE,
                    ResumeSection.INTERNSHIPS, ResumeSection.CERTIFICATIONS,
                    ResumeSection.EDUCATION
            ), a.recommendedSectionOrder());
            assertEquals(a.recommendedSectionOrder(),
                    analyze(swProfile(), job("Java Developer", "backend",
                            List.of("Java", "Spring Boot", "PostgreSQL"), List.of(), "2 years"))
                            .recommendedSectionOrder());
        }

        @Test
        @DisplayName("hardware/VLSI job ordering differs appropriately with INTERNSHIPS before EXPERIENCE")
        void hardwareOrdering() {
            CandidateProfile c = profile("Bob",
                    List.of(), List.of("Verilog"), List.of(),
                    List.of("RTL design using Verilog"),
                    List.of("2 years digital design"),
                    List.of("FPGA design intern at Gravton"),
                    List.of(), List.of("B.E. ECE"), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("VLSI Design Engineer", "vlsi rtl verification",
                            List.of("Verilog"), List.of("FPGA"), "2 years"));

            assertEquals(List.of(
                    ResumeSection.SUMMARY, ResumeSection.SKILLS,
                    ResumeSection.PROJECTS,
                    ResumeSection.INTERNSHIPS, ResumeSection.EXPERIENCE,
                    ResumeSection.EDUCATION
            ), a.recommendedSectionOrder());
        }

        @Test
        @DisplayName("empty sections are not artificially prioritized")
        void emptySectionsNotPrioritized() {
            // A candidate with no projects/certifications — those must not be forced in.
            ResumeTailoringAnalysis a = analyze(
                    profile("Alice", List.of("Java"), List.of(), List.of(),
                            List.of(), List.of("2 years at Acme"), List.of(),
                            List.of(), List.of()),
                    job("Java Developer", "backend", List.of("Java"), List.of(), "2 years"));

            assertFalse(a.recommendedSectionOrder().contains(ResumeSection.PROJECTS));
            assertFalse(a.recommendedSectionOrder().contains(ResumeSection.CERTIFICATIONS));
        }
    }

    // ─── 17–19. ATS readiness ───────────────────────────────────────────────

    @Nested
    @DisplayName("ATS readiness")
    class ReadinessTests {

        @Test
        @DisplayName("score and explanation are deterministic and consistent with counts")
        void deterministicReadiness() {
            ResumeTailoringAnalysis a = analyze(
                    profile("Alice", List.of("Java", "Spring Boot"), List.of(), List.of(),
                            List.of("App using Java, Spring Boot"),
                            List.of("1 year with Spring Boot"), List.of(),
                            List.of(), List.of(), List.of()),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot"), List.of("Docker"), "2 years"));

            AtsReadinessAnalysis r = a.atsReadiness();
            // 60*1.0 + 20*0 + 10 + 10 = 80
            assertEquals(80, r.score());
            assertTrue(r.label().contains("advisory"));
            assertFalse(r.label().contains("Guaranteed"));
            assertEquals(2, r.matchedRequiredCount());
            assertEquals(0, r.missingRequiredCount());
            assertEquals(0, r.matchedPreferredCount());
            assertEquals(1, r.missingPreferredCount());
            assertTrue(r.relevantProjectEvidence());
            assertTrue(r.relevantExperienceEvidence());
            assertEquals(r, analyze(
                    profile("Alice", List.of("Java", "Spring Boot"), List.of(), List.of(),
                            List.of("App using Java, Spring Boot"),
                            List.of("1 year with Spring Boot"), List.of(),
                            List.of(), List.of(), List.of()),
                    job("Java Developer", "backend",
                            List.of("Java", "Spring Boot"), List.of("Docker"), "2 years"))
                    .atsReadiness());
        }

        @Test
        @DisplayName("score formula weights required coverage most heavily")
        void requiredWeighedMost() {
            // Both jobs have an unmatched preferred skill so the preferred term is 0 for each,
            // isolating the required-coverage contribution.
            ResumeTailoringAnalysis full = analyze(
                    profile("A", List.of("Java", "Spring Boot"), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                    job("Dev", "backend", List.of("Java", "Spring Boot"), List.of("Docker"), "2 years"));
            ResumeTailoringAnalysis partial = analyze(
                    profile("B", List.of("Java"), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                    job("Dev", "backend", List.of("Java", "Spring Boot"), List.of("Docker"), "2 years"));

            // Full required coverage: 60 + 0 + 0 + 0 = 60.
            assertEquals(60, full.atsReadiness().score());
            // Half required coverage: 30 + 0 + 0 + 0 = 30.
            assertEquals(30, partial.atsReadiness().score());
        }
    }

    // ─── 20–28. Robustness & non-mutation ───────────────────────────────────

    @Nested
    @DisplayName("Robustness and non-mutation")
    class RobustnessTests {

        @Test
        @DisplayName("repeated identical input produces identical analysis (20 times)")
        void identicalInputIdenticalOutput() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of("App using Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of());
            Job j = job("Java Developer", "backend", List.of("Java"), List.of(), "2 years");
            ResumeTailoringAnalysis first = analyze(c, j);
            for (int i = 0; i < 20; i++) {
                assertEquals(first, analyze(c, j));
            }
        }

        @Test
        @DisplayName("null candidate + gap + job are handled safely")
        void nullSafe() {
            ResumeTailoringAnalysis a = tailoringService.analyze(null, job("x", "y", List.of(), List.of(), null), null);
            assertTrue(a.highlightedSkills().isEmpty());
            assertTrue(a.missingRequiredSkills().isEmpty());
            assertTrue(a.atsReadiness().score() >= 0 && a.atsReadiness().score() <= 100);
        }

        @Test
        @DisplayName("empty candidate profile yields a sensible no-fabrication analysis")
        void emptyProfile() {
            ResumeTailoringAnalysis a = analyze(
                    profile("", List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                    job("Java Developer", "backend", List.of("Java"), List.of(), "2 years"));

            assertTrue(a.matchedRequiredSkills().isEmpty());
            assertTrue(a.missingRequiredSkills().contains("Java"));
            assertTrue(a.highlightedSkills().isEmpty());
            assertTrue(a.relevantProjects().isEmpty());
        }

        @Test
        @DisplayName("empty/minimal job yields cleared analysis")
        void emptyJob() {
            ResumeTailoringAnalysis a = analyze(
                    profile("Alice", List.of("Java"), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                    new Job(null, null, null, null, null, List.of(), List.of(),
                            null, null, null, null, null, null, null));

            assertTrue(a.missingRequiredSkills().isEmpty());
            assertTrue(a.missingPreferredSkills().isEmpty());
            assertTrue(a.recommendedSectionOrder().get(0) == ResumeSection.SUMMARY);
        }

        @Test
        @DisplayName("candidateId stays null when unavailable (never derived from PII)")
        void noInventedCandidateId() {
            ResumeTailoringAnalysis a = analyze(
                    profile("Alice", List.of("Java"), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                    job("Dev", "backend", List.of("Java"), List.of(), "2 years"));
            // Pass an analysis with null candidateId and null id should propagate.
            assertEquals(null, a.candidateId());
        }

        @Test
        @DisplayName("no fabricated skills, experience, projects, or certifications")
        void nothingFabricated() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("Java Developer", "backend",
                            List.of("Java", "Docker", "Kubernetes"), List.of(), "2 years"));

            assertTrue(a.matchedRequiredSkills().equals(List.of("Java")));
            assertTrue(a.missingRequiredSkills().contains("Docker"));
            assertTrue(a.missingRequiredSkills().contains("kubernetes"));
            // Docker / Kubernetes are MISSING, never highlighted or added to verified skills.
            assertFalse(highlightedNames(a).contains("Docker"));
            assertFalse(highlightedNames(a).contains("kubernetes"));
        }

        @Test
        @DisplayName("original CandidateProfile and Job are not modified")
        void inputsNotModified() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of("App using Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of());
            Job j = job("Java Developer", "backend", List.of("Java"), List.of(), "2 years");
            List<String> skillsBefore = new ArrayList<>(c.softwareSkills());
            List<String> reqBefore = new ArrayList<>(j.requiredSkills());

            analyze(c, j);
            analyze(c, j);

            assertEquals(skillsBefore, c.softwareSkills());
            assertEquals(reqBefore, j.requiredSkills());
        }

        @Test
        @DisplayName("original CareerGapAnalysis is not modified")
        void gapNotModified() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("Java Developer", "backend", List.of("Java"), List.of(), "2 years");
            CareerGapAnalysis g = gap(c, j);
            List<String> missingBefore = new ArrayList<>(g.missingRequiredSkills().stream()
                    .map(sg -> sg.canonicalSkill()).toList());

            tailoringService.analyze(c, j, g);

            assertEquals(missingBefore, g.missingRequiredSkills().stream()
                    .map(sg -> sg.canonicalSkill()).toList());
        }
    }
}