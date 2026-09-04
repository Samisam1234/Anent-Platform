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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4.5 — safe, deterministic tailored resume draft generator.
 *
 * <p>Uses the real {@link CareerGapAnalysisService} and {@link ResumeTailoringAnalysisService}
 * to build the analysis, then {@link TailoredResumeDraftService}. All fixtures are hermetic;
 * no LLM, database or network. Emphasis on safety: the draft never fabricates content and the
 * originals are never mutated.</p>
 */
@DisplayName("TailoredResumeDraftService — safe deterministic resume draft (Phase 4.5)")
class TailoredResumeDraftServiceTest {

    private final CareerGapAnalysisService gapService =
            new CareerGapAnalysisService(new SkillMatchingEngine(), new CareerTrackEngine());
    private final ResumeTailoringAnalysisService tailoringService =
            new ResumeTailoringAnalysisService(new CareerTrackEngine());
    private final TailoredResumeDraftService draftService = new TailoredResumeDraftService();

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private Job job(String id, String title, String description, List<String> required,
                    List<String> preferred, String exp) {
        return new Job(id, title, "Acme", "Remote", description,
                required, preferred, exp, "FULL_TIME", "2026-08-25", "MOCK_SOURCE",
                null, "MOCK", null);
    }

    private CandidateProfile profile(String name, List<String> software, List<String> hardware,
                                     List<String> skills, List<String> projects,
                                     List<String> experience, List<String> internships,
                                     List<String> certifications, List<String> education) {
        return new CandidateProfile(name, null, null, null, education, skills,
                experience, internships, projects, certifications, software, hardware,
                List.of(), List.of());
    }

    private ResumeTailoringAnalysis analyze(CandidateProfile c, Job j) {
        return tailoringService.analyze(c, j, gapService.analyze(c, j));
    }

    private TailoredResumeDraft draft(CandidateProfile c, Job j) {
        return draftService.generate(c, j, analyze(c, j));
    }

    // ─── 1–2. Immutability ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Input immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("original CandidateProfile remains unchanged")
        void candidateUnchanged() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "Spring Boot"), List.of(), List.of(),
                    List.of("Hospital System using Java, Spring Boot"),
                    List.of("Backend developer using Spring Boot"), List.of(),
                    List.of(), List.of());
            List<String> swBefore = new ArrayList<>(c.softwareSkills());
            List<String> projBefore = new ArrayList<>(c.projects());
            List<String> expBefore = new ArrayList<>(c.experience());

            draft(c, job("j1", "Java Developer", "backend",
                    List.of("Java", "Spring Boot"), List.of(), "2 years"));

            assertEquals(swBefore, c.softwareSkills());
            assertEquals(projBefore, c.projects());
            assertEquals(expBefore, c.experience());
        }

        @Test
        @DisplayName("original Job remains unchanged")
        void jobUnchanged() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "backend", List.of("Java"), List.of(), "2 years");
            List<String> reqBefore = new ArrayList<>(j.requiredSkills());
            List<String> prefBefore = new ArrayList<>(j.preferredSkills());

            draft(c, j);

            assertEquals(reqBefore, j.requiredSkills());
            assertEquals(prefBefore, j.preferredSkills());
        }

        @Test
        @DisplayName("original ResumeTailoringAnalysis is not mutated by the draft generator")
        void analysisUnchanged() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            ResumeTailoringAnalysis a = analyze(c,
                    job("j1", "Dev", "backend", List.of("Java", "Docker"), List.of(), "2 years"));
            List<String> missingBefore = new ArrayList<>(a.missingRequiredSkills());

            draftService.generate(c, job("j1", "Dev", "backend",
                    List.of("Java", "Docker"), List.of(), "2 years"), a);

            assertEquals(missingBefore, a.missingRequiredSkills());
        }
    }

    // ─── 3–7. Missing & skill ordering ─────────────────────────────────────

    @Nested
    @DisplayName("Skill ordering & missing-requirement safety")
    class SkillTests {

        @Test
        @DisplayName("missing skills are never added to the draft's ordered skills")
        void missingSkillsNeverAdded() {
            // Job asks for Java + Docker (Docker missing); candidate has Java only.
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Java Developer", "backend",
                    List.of("Java", "Docker"), List.of("Kubernetes"), "2 years"));

            assertTrue(d.orderedSkills().contains("Java"));
            assertFalse(d.orderedSkills().contains("Docker"));
            assertFalse(d.orderedSkills().contains("kubernetes"));
        }

        @Test
        @DisplayName("required-job highlighted skills appear before preferred and other skills")
        void requiredBeforePreferredBeforeOthers() {
            // Java is a matched required skill; Docker is a matched preferred skill;
            // Rust is an existing candidate skill unrelated to the job.
            // Rust is not a registered taxonomy alias, so it canonicalizes to lowercase "rust";
            // Docker and Java are canonical taxonomy names as-is.
            CandidateProfile c = profile("Alice",
                    List.of("Rust", "Java", "Docker"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Backend", "backend",
                    List.of("Java"), List.of("Docker"), "2 years"));

            assertTrue(d.orderedSkills().indexOf("Java") >= 0);
            assertTrue(d.orderedSkills().indexOf("Docker") >= 0);
            assertTrue(d.orderedSkills().indexOf("rust") >= 0);
            assertTrue(d.orderedSkills().indexOf("Java") < d.orderedSkills().indexOf("Docker"),
                    "required before preferred");
            assertTrue(d.orderedSkills().indexOf("Docker") < d.orderedSkills().indexOf("rust"),
                    "preferred before unrelated skills");
        }

        @Test
        @DisplayName("duplicate skills are removed")
        void duplicatesRemoved() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "PostgreSQL"), List.of(), List.of("Java", "Postgres"),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java"), List.of(), "2 years"));

            List<String> skills = d.orderedSkills();
            assertEquals(skills.size(), skills.stream().distinct().count());
        }

        @Test
        @DisplayName("skill aliases are canonicalized (Postgres → PostgreSQL)")
        void aliasesCanonicalized() {
            CandidateProfile c = profile("Alice",
                    List.of("Postgres"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("PostgreSQL"), List.of(), "2 years"));

            assertTrue(d.orderedSkills().contains("PostgreSQL"));
            assertFalse(d.orderedSkills().contains("Postgres"));
        }
    }

    // ─── 8–13. Projects / experience / internships provenance ──────────────

    @Nested
    @DisplayName("Content provenance — only existing candidate entries")
    class ProvenanceTests {

        @Test
        @DisplayName("projects come only from CandidateProfile.projects()")
        void projectsOnlyFromProfile() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of("Hospital System using Java"),
                    List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java"), List.of(), "2 years"));

            // Relevant project listed first, original text preserved.
            assertEquals(List.of("Hospital System using Java"), d.highlightedProjects());
            // Nothing invented — every project entry maps to a real candidate project.
            assertTrue(d.highlightedProjects().stream()
                    .allMatch(p -> c.projects().contains(p)));
        }

        @Test
        @DisplayName("experience comes only from CandidateProfile.experience()")
        void experienceOnlyFromProfile() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of(),
                    List.of("Backend developer using Java at Acme"),
                    List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java"), List.of(), "2 years"));

            assertEquals(List.of("Backend developer using Java at Acme"),
                    d.highlightedExperience());
            assertTrue(d.highlightedExperience().stream()
                    .allMatch(e -> c.experience().contains(e)));
        }

        @Test
        @DisplayName("internships come only from CandidateProfile.internships()")
        void internshipsOnlyFromProfile() {
            CandidateProfile c = profile("Bob",
                    List.of(), List.of("FPGA"), List.of(),
                    List.of(),
                    List.of(),
                    List.of("FPGA design intern at Gravton (Xilinx)"),
                    List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "VLSI Engineer", "vlsi rtl",
                    List.of("Verilog"), List.of("FPGA"), "2 years"));

            assertFalse(d.highlightedInternships().isEmpty());
            assertTrue(d.highlightedInternships().stream()
                    .allMatch(i -> c.internships().contains(i)));
        }

        @Test
        @DisplayName("no fake project, experience, or internship is created")
        void nothingFabricated() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java", "Docker", "Kubernetes"), List.of(), "2 years"));

            assertTrue(d.highlightedProjects().isEmpty());
            assertTrue(d.highlightedExperience().isEmpty());
            assertTrue(d.highlightedInternships().isEmpty());
            // Missing requirements never surface as content.
            assertFalse(d.orderedSkills().contains("Docker"));
            assertFalse(d.orderedSkills().contains("kubernetes"));
        }
    }

    // ─── 14–17. Safety, origin, determinism, empties ───────────────────────

    @Nested
    @DisplayName("Safety, origin, determinism & emptiness")
    class SafetyTests {

        @Test
        @DisplayName("missing requirements never appear as skills; only as warnings")
        void missingOnlyInWarnings() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java", "Docker"), List.of("Kubernetes"), "2 years"));

            assertFalse(d.orderedSkills().contains("Docker"));
            assertFalse(d.orderedSkills().contains("kubernetes"));
            assertFalse(d.highlightedProjects().contains("Docker"));
            assertFalse(d.highlightedExperience().contains("Docker"));
            // The missing-requirement note is present among the warnings.
            assertTrue(d.warnings().stream().anyMatch(w -> w.contains("missing")));
        }

        @Test
        @DisplayName("warnings explain the safety behavior")
        void warningsExplainSafety() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of("App using Java"), List.of(), List.of(), List.of(), List.of());
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java"), List.of(), "2 years"));

            assertTrue(d.warnings().stream().anyMatch(w -> w.contains("NOT added")));
            assertTrue(d.warnings().stream().anyMatch(w ->
                    w.toLowerCase().contains("only existing candidate information")));
            assertTrue(d.warnings().stream().anyMatch(w -> w.contains("NOT modified")));
        }

        @Test
        @DisplayName("draft origin is DETERMINISTIC")
        void originDeterministic() {
            TailoredResumeDraft d = draft(
                    profile("Alice", List.of("Java"), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of()),
                    job("j1", "Dev", "backend", List.of("Java"), List.of(), "2 years"));
            assertEquals(DraftOrigin.DETERMINISTIC, d.origin());
        }

        @Test
        @DisplayName("repeated identical input produces identical output (20 times)")
        void deterministicRepeat() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "Spring Boot"), List.of(), List.of(),
                    List.of("App using Java, Spring Boot"),
                    List.of("1 year with Spring Boot"), List.of(),
                    List.of(), List.of());
            Job j = job("j1", "Java Developer", "backend",
                    List.of("Java", "Spring Boot"), List.of("Docker"), "2 years");
            TailoredResumeDraft first = draft(c, j);
            for (int i = 0; i < 20; i++) {
                assertEquals(first, draft(c, j));
            }
        }
    }

    // ─── 18–21. Empty / null / summary / section order ─────────────────────

    @Nested
    @DisplayName("Empty, null, summary & section ordering")
    class RobustnessTests {

        @Test
        @DisplayName("empty profile is handled safely")
        void emptyProfileSafe() {
            TailoredResumeDraft d = draft(
                    profile("", List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of()),
                    job("j1", "Dev", "backend", List.of("Java"), List.of(), "2 years"));

            assertTrue(d.orderedSkills().isEmpty());
            assertTrue(d.highlightedProjects().isEmpty());
            assertTrue(d.highlightedExperience().isEmpty());
            assertTrue(d.highlightedInternships().isEmpty());
            assertFalse(d.professionalSummary().isBlank());
            assertNotNull(d.sectionOrder());
        }

        @Test
        @DisplayName("null candidate, job, and analysis are handled safely (no NPE)")
        void nullSafe() {
            TailoredResumeDraft d = draftService.generate(null, null, null);
            assertNotNull(d);
            assertNull(d.candidateId());
            assertTrue(d.orderedSkills().isEmpty());
            assertTrue(d.highlightedProjects().isEmpty());
            assertFalse(d.professionalSummary().isBlank());
            assertEquals(DraftOrigin.DETERMINISTIC, d.origin());
        }

        @Test
        @DisplayName("professional summary is conservative and uses only existing information")
        void summaryConservative() {
            CandidateProfile c = profile("Alice",
                    List.of("Java", "Spring Boot"), List.of(), List.of("PostgreSQL"),
                    List.of("Hospital System using Java"),
                    List.of("1 year backend developer with Spring Boot"),
                    List.of(), List.of(), List.of("B.Tech CSE"));
            TailoredResumeDraft d = draft(c, job("j1", "Java Developer", "backend",
                    List.of("Java", "Spring Boot"), List.of(), "2 years"));

            String s = d.professionalSummary();
            assertTrue(s.contains("Graduate"));
            assertTrue(s.contains("knowledge of"));
            assertFalse(s.toLowerCase().contains("expert"));
            assertFalse(s.toLowerCase().contains("professional"));
        }

        @Test
        @DisplayName("section order is deterministic and includes only sections with content")
        void sectionOrderDeterministicAndReflective() {
            CandidateProfile c = profile("Alice",
                    List.of("Java"), List.of(), List.of(),
                    List.of("App using Java"),
                    List.of("Backend dev using Java"),
                    List.of("Intern building REST APIs"),
                    List.of(), List.of("B.Tech CSE"));
            TailoredResumeDraft d = draft(c, job("j1", "Dev", "backend",
                    List.of("Java"), List.of(), "2 years"));

            // SUMMARY, SKILLS, then content sections; CERTIFICATIONS absent (no content).
            List<ResumeSection> order = d.sectionOrder();
            assertEquals(ResumeSection.SUMMARY, order.get(0));
            assertTrue(order.contains(ResumeSection.SKILLS));
            assertTrue(order.contains(ResumeSection.PROJECTS));
            assertTrue(order.contains(ResumeSection.EXPERIENCE));
            assertTrue(order.contains(ResumeSection.INTERNSHIPS));
            assertFalse(order.contains(ResumeSection.CERTIFICATIONS));

            // Deterministic across re-runs.
            assertEquals(order, draft(c, job("j1", "Dev", "backend",
                    List.of("Java"), List.of(), "2 years")).sectionOrder());
        }
    }
}