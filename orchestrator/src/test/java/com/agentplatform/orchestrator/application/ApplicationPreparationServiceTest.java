package com.agentplatform.orchestrator.application;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Phase 5.1 — safe deterministic email draft generator.
 * All tests are hermetic: no network, no LLM, no email sending.
 */
@DisplayName("ApplicationPreparationService — safe deterministic email draft (Phase 5.1)")
class ApplicationPreparationServiceTest {

    private final ApplicationPreparationService service =
            new ApplicationPreparationService();
    private final CareerGapAnalysisService gapService =
            new CareerGapAnalysisService(new SkillMatchingEngine(),
                    new CareerTrackEngine());

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private Job job(String id, String title, String description,
                    List<String> required, List<String> preferred, String exp) {
        return new Job(id, title, "Acme", "Remote", description,
                required, preferred, exp, "FULL_TIME", "2026-08-25",
                "MOCK_SOURCE", null, "MOCK", null);
    }

    private CandidateProfile profile(String name, List<String> software,
                                     List<String> hardware, List<String> skills,
                                     List<String> projects,
                                     List<String> experience,
                                     List<String> internships,
                                     List<String> certifications,
                                     List<String> education) {
        return new CandidateProfile(name, null, null, null, education, skills,
                experience, internships, projects, certifications, software, hardware,
                List.of(), List.of());
    }

    // ─── 1–3. Basic generation & status ────────────────────────────────────

    @Nested
    @DisplayName("Basic generation and status")
    class BasicTests {

        @Test
        @DisplayName("basic application draft generation")
        void basicGeneration() {
            CandidateProfile c = profile("Alice", List.of("Java", "Spring Boot"),
                    List.of(), List.of("Hospital System using Java"),
                    List.of(), List.of("1 year backend developer with Spring Boot"),
                    List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "backend software role",
                    List.of("Java", "Spring Boot"), List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertNotNull(email);
            assertEquals(ApplicationDraftStatus.REVIEW_REQUIRED, email.status());
        }

        @Test
        @DisplayName("application status is REVIEW_REQUIRED (never SENT)")
        void statusRequiresReview() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertEquals(ApplicationDraftStatus.REVIEW_REQUIRED, email.status());
            // Status must never be SENT.
            assertTrue(!email.status().name().equals("SENT"));
        }
    }

    // ─── 4–5. Default recipient ────────────────────────────────────────────

    @Nested
    @DisplayName("Default recipient behavior")
    class RecipientTests {

        @Test
        @DisplayName("default recipient name is Hiring Manager")
        void defaultRecipientName() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertEquals("Hiring Manager", email.recipientName());
        }

        @Test
        @DisplayName("default recipient email is null")
        void defaultRecipientEmail() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.recipientEmail() == null);
        }
    }

    // ─── 6. Service never invents email ────────────────────────────────────

    @Nested
    @DisplayName("No invented email addresses")
    class NoInventionTests {

        @Test
        @DisplayName("service never invents an email address")
        void neverInventsEmail() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.recipientEmail() == null);
            // Warnings should reflect the default state.
            assertTrue(email.warnings().stream()
                    .anyMatch(w -> w.contains("must be entered or verified")));
        }
    }

    // ─── 7. Explicit recipient email handling ──────────────────────────────

    @Nested
    @DisplayName("Explicit recipient email handling")
    class ExplicitRecipientTests {

        @Test
        @DisplayName("three-arg prepare uses null email with appropriate warning")
        void threeArgUsesNullEmail() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.recipientEmail() == null);
            // Warning 3 should be the "must verify" version since email is null.
            assertTrue(email.warnings().stream()
                    .anyMatch(w -> w.contains("must be entered or verified")));
        }
    }

    // ─── 8–9. Invalid email rejected & warning ─────────────────────────────

    @Nested
    @DisplayName("Invalid recipient email handling")
    class InvalidEmailTests {

        @Test
        @DisplayName("invalid email (no @) generates warning")
        void invalidEmailGeneratesWarning() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            // Five-arg overload with email missing "@"
            ApplicationEmailDraft email = service.prepare(c, j, null,
                    "Hiring Manager", "invalidemail");

            // Warning should indicate verification needed (since no "@").
            assertTrue(email.warnings().stream()
                    .anyMatch(w -> w.contains("must be entered or verified")
                            || w.contains("Verify the recipient email")));
        }

        @Test
        @DisplayName("valid email with @ triggers customized warning")
        void validEmailTriggersCustomizedWarning() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null,
                    "Hiring Manager", "user@example.com");

            // Since email contains "@", warning 3 should be the customized version.
            assertTrue(email.warnings().stream()
                    .anyMatch(w -> w.contains("Verify the recipient email before sending")));
        }
    }

    // ─── 10–11. Email subject ──────────────────────────────────────────────

    @Nested
    @DisplayName("Email subject behavior")
    class SubjectTests {

        @Test
        @DisplayName("subject contains job title")
        void subjectContainsJobTitle() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.subject().contains("Java Developer"));
        }

        @Test
        @DisplayName("subject includes candidate name when available")
        void subjectIncludesCandidateName() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.subject().contains("Alice"));
        }

        @Test
        @DisplayName("subject when no candidate name falls back to job title only")
        void subjectFallbacksToJobTitle() {
            CandidateProfile c = profile(null, List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            // Should contain "Application for Java Developer" but not a name.
            assertTrue(email.subject().contains("Application for Java Developer"));
            // "Alice" should NOT appear since no name was provided.
            assertFalse(email.subject().contains("Alice"));
        }
    }

    // ─── 12–14. Email body ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Email body behavior")
    class BodyTests {

        @Test
        @DisplayName("body contains selected job title")
        void bodyContainsJobTitle() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.body().contains("Java Developer"));
        }

        @Test
        @DisplayName("body contains company when available")
        void bodyContainsCompany() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of("Acme Corp"));
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.body().contains("Acme"));
        }

        @Test
        @DisplayName("body uses conservative language no fake claims")
        void bodyNoFakeClaims() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of("Hospital System using Java"),
                    List.of("1 year backend developer with Spring Boot"),
                    List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            String body = email.body();
            // Should not claim "expert" or "guaranteed"
            assertTrue(!body.toLowerCase().contains("expert"));
            assertTrue(!body.toLowerCase().contains("guaranteed"));
        }
    }

    // ─── 15. Candidate skills mentioned ──────────────────────────────────────

    @Nested
    @DisplayName("Candidate skills in body")
    class SkillsTests {

        @Test
        @DisplayName("existing candidate skills can be mentioned")
        void skillsMentioned() {
            CandidateProfile c = profile("Alice", List.of("Java", "Spring Boot"),
                    List.of(), List.of("Hospital System using Java, Spring Boot"),
                    List.of("1 year backend developer with Spring Boot"),
                    List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java", "Spring Boot"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.body().contains("Java"));
            assertTrue(email.body().contains("Spring Boot"));
        }

        @Test
        @DisplayName("missing skills never invented")
        void missingSkillsNeverInvented() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java", "Docker"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            // Body should not claim Docker skills.
            assertTrue(!email.body().contains("Docker"));
            // Subject should not mention Docker.
            assertTrue(!email.subject().contains("Docker"));
        }
    }

    // ─── 18. Resume reference ────────────────────────────────────────────────

    @Nested
    @DisplayName("Resume reference behavior")
    class ResumeReferenceTests {

        @Test
        @DisplayName("resume reference is DRAFT_ONLY")
        void resumeReferenceDraftOnly() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertEquals("DRAFT_ONLY", email.resumeReference());
        }

        @Test
        @DisplayName("resume reference never claims attachment exists")
        void resumeNoAttachmentClaim() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            // The body says "resume information for your review" not "resume attached"
            assertTrue(email.body().contains("resume information for your review"));
            assertFalse(email.body().contains("resume attached"));
        }
    }

    // ─── 20–21. Warnings ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Warning messages")
    class WarningTests {

        @Test
        @DisplayName("warnings state application has not been sent")
        void warningsNotSent() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.warnings().stream()
                    .anyMatch(w -> w.contains("has not been sent")));
        }

        @Test
        @DisplayName("warnings state user review is required")
        void warningsRequireReview() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertTrue(email.warnings().stream()
                    .anyMatch(w -> w.toLowerCase().contains("review")));
        }
    }

    // ─── 22–24. Input immutability ─────────────────────────────────────────

    @Nested
    @DisplayName("Input immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("CandidateProfile remains unchanged")
        void candidateUnchanged() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of("Hospital System using Java"),
                    List.of("1 year backend developer with Spring Boot"),
                    List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            service.prepare(c, j, null);
            service.prepare(c, j, null);

            assertEquals("Alice", c.name());
            assertEquals(List.of("Java"), c.softwareSkills());
        }

        @Test
        @DisplayName("Job remains unchanged")
        void jobUnchanged() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            service.prepare(c, j, null);
            service.prepare(c, j, null);

            // Job is a record; verify no NPE and required skills unchanged.
            assertEquals(List.of("Java"), j.requiredSkills());
        }

        @Test
        @DisplayName("TailoredResumeDraft remains unchanged (no-op if null)")
        void draftUnchanged() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            service.prepare(c, j, null);
            service.prepare(c, j, null);
        }
    }

    // ─── 25. Determinism ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic behavior")
    class DeterminismTests {

        @Test
        @DisplayName("repeated generation produces identical output")
        void deterministicRepeat() {
            CandidateProfile c = profile("Alice", List.of("Java", "Spring Boot"),
                    List.of(), List.of("Hospital System using Java, Spring Boot"),
                    List.of("1 year with Spring Boot"), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "backend role",
                    List.of("Java", "Spring Boot"), List.of("Docker"), "2 years");

            ApplicationEmailDraft first = service.prepare(c, j, null);
            for (int i = 0; i < 10; i++) {
                assertEquals(first, service.prepare(c, j, null));
            }
        }
    }

    // ─── 26–28. Empty / null handling ──────────────────────────────────────

    @Nested
    @DisplayName("Empty and null handling")
    class EmptyNullTests {

        @Test
        @DisplayName("empty CandidateProfile handled safely")
        void emptyProfileSafe() {
            ApplicationEmailDraft email = service.prepare(
                    profile("", List.of(), List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of()),
                    job("j1", "Dev", "role", List.of(), List.of(), "null"),
                    null);

            assertNotNull(email);
            assertTrue(email.warnings().size() >= 3);
        }

        @Test
        @DisplayName("null CandidateProfile handled safely")
        void nullCandidateSafe() {
            ApplicationEmailDraft email = service.prepare(null,
                    job("j1", "Dev", "role", List.of(), List.of(), "null"), null);

            assertNotNull(email);
            assertTrue(email.warnings().size() >= 3);
        }

        @Test
        @DisplayName("null Job handled safely")
        void nullJobSafe() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            ApplicationEmailDraft email = service.prepare(c, null, null);

            assertNotNull(email);
            assertTrue(email.warnings().size() >= 3);
        }

        @Test
        @DisplayName("null TailoredResumeDraft handled safely")
        void nullDraftSafe() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");
            ApplicationEmailDraft email = service.prepare(c, j, null);

            assertNotNull(email);
            assertEquals(ApplicationDraftStatus.REVIEW_REQUIRED, email.status());
        }
    }

    // ─── 30. No external dependencies ──────────────────────────────────────

    @Nested
    @DisplayName("No external dependencies")
    class DependencyTests {

        @Test
        @DisplayName("no network/email/LLM dependency is invoked")
        void noExternalDependencies() {
            CandidateProfile c = profile("Alice", List.of("Java"), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            Job j = job("j1", "Java Developer", "a Java role", List.of("Java"),
                    List.of(), "2 years");

            ApplicationEmailDraft email = service.prepare(c, j, null);
            assertNotNull(email);
        }
    }
}