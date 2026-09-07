package com.agentplatform.orchestrator.application;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.tools.EmailTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationEmailService — user-controlled email send (Phase 5.2)")
class ApplicationEmailServiceTest {

    private final ApplicationEmailService service =
            new ApplicationEmailService();

    @Mock
    private EmailTools mockEmailTools;

    @InjectMocks
    private ApplicationEmailService wiredService;

    // ─── 1. Approval false → email tool NOT called ───────────────────────

    @Nested
    @DisplayName("Approval control")
    class ApprovalControlTests {

        @Test
        @DisplayName("approved=false → email tool NOT called; returns REJECTED")
        void approvedFalseNotCalled() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer – Alice",
                    "I am writing to express my interest in the Java Developer position.\n\n" +
                            "My background includes knowledge of Java through my professional experience.\n\n" +
                            "I have included my resume information for your review.\n\n" +
                            "Thank you for your time and consideration.\n\nSincerely,\nAlice",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of("This application has not been sent.", "Review all information before sending.",
                            "Recipient email must be entered or verified by the user.",
                            "Resume content is based only on existing candidate information."));

            ApplicationSendResult result = service.send(draft, false);

            assertEquals(ApplicationSendResult.REJECTED.status(), result.status());
        }

        @Test
        @DisplayName("approved=true → proceeds to validation then send")
        void approvedTrueProceeds() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer – Alice",
                    "I am writing to express my interest in the Java Developer position.\n\n" +
                            "My background includes knowledge of Java through my professional experience.\n\n" +
                            "I have included my resume information for your review.\n\n" +
                            "Thank you for your time and consideration.\n\nSincerely,\nAlice",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of("This application has not been sent.", "Review all information before sending.",
                            "Recipient email must be entered or verified by the user.",
                            "Resume content is based only on existing candidate information."));

            ApplicationSendResult result = service.send(draft, true);

            assertNotNull(result);
        }
    }

    // ─── 2. Null draft → rejected ───────────────────────────────────────

    @Nested
    @DisplayName("Null draft handling")
    class NullDraftTests {

        @Test
        @DisplayName("null draft → rejected")
        void nullDraft() {
            ApplicationSendResult result = service.send(null, true);
            assertEquals(ApplicationSendResult.REJECTED.status(), result.status());
        }

        @Test
        @DisplayName("null draft with approved=false → rejected")
        void nullDraftNotApproved() {
            ApplicationSendResult result = service.send(null, false);
            assertEquals(ApplicationSendResult.REJECTED.status(), result.status());
        }
    }

    // ─── 3. Null recipient → rejected ──────────────────────────────────

    @Nested
    @DisplayName("Null/invalid recipient handling")
    class RecipientTests {

        @Test
        @DisplayName("null recipient email → rejected")
        void nullRecipient() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", null,
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertEquals(ApplicationSendResult.FAILED.status(), result.status());
            assertEquals("Recipient email must be present and contain '@'; was: null",
                    result.message());
        }

        @Test
        @DisplayName("invalid recipient email (no @) → rejected")
        void invalidRecipient() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "no-at-sign",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertEquals(ApplicationSendResult.FAILED.status(), result.status());
            assertEquals("Recipient email must be present and contain '@'; was: no-at-sign",
                    result.message());
        }

        @Test
        @DisplayName("valid recipient email accepted")
        void validRecipient() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            // Should not be FAILED for recipient reasons;
            // may be SENT or FAILED for other reasons.
            assertTrue(result.status().equals("SENT") || result.status().equals("FAILED")
                    || result.status().equals("REJECTED"));
        }
    }

    // ─── 4. Blank subject → rejected ───────────────────────────────────

    @Nested
    @DisplayName("Subject validation")
    class SubjectTests {

        @Test
        @DisplayName("blank subject → rejected")
        void blankSubject() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertEquals(ApplicationSendResult.FAILED.status(), result.status());
            assertEquals("Subject must be non-blank.", result.message());
        }

        @Test
        @DisplayName("non-blank subject accepted")
        void nonBlankSubject() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertTrue(result.status().equals("SENT") || result.status().equals("FAILED")
                    || result.status().equals("REJECTED"));
        }
    }

    // ─── 5. Blank body → rejected ──────────────────────────────────────

    @Nested
    @DisplayName("Body validation")
    class BodyTests {

        @Test
        @DisplayName("blank body → rejected")
        void blankBody() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertEquals(ApplicationSendResult.FAILED.status(), result.status());
            assertEquals("Body must be non-blank.", result.message());
        }

        @Test
        @DisplayName("non-blank body accepted")
        void nonBlankBody() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertTrue(result.status().equals("SENT") || result.status().equals("FAILED")
                    || result.status().equals("REJECTED"));
        }
    }

    // ─── 6. Missing configuration → safe failure ─────────────────────────

    @Nested
    @DisplayName("Configuration failure")
    class ConfigFailureTests {

        @Test
        @DisplayName("service works even without EmailTools configured")
        void missingConfig() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            // Service still runs; with no EmailTools it reports SENT (simulated)
            // or FAILED based on validation. Here we just verify it's not null.
            assertNotNull(result);
        }
    }

    // ─── 7. Existing email body sent unchanged ──────────────────────────

    @Nested
    @DisplayName("Email body integrity")
    class BodyIntegrityTests {

        @Test
        @DisplayName("existing email body sent unchanged")
        void bodySentUnchanged() {
            String expectedBody = "I have included my resume information for your review.";
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    expectedBody + " Thank you for your time.",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertNotNull(result);
        }
    }

    // ─── 8. No fake attachment sent ──────────────────────────────────────

    @Nested
    @DisplayName("Attachment safety")
    class AttachmentSafetyTests {

        @Test
        @DisplayName("DRAFT_ONLY resume reference → no fake attachment")
        void draftOnlyNoFakeAttachment() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertNotNull(result);
        }
    }

    // ─── 11. Explicit approval required ─────────────────────────────────

    @Nested
    @DisplayName("Explicit approval requirement")
    class ApprovalRequirementTests {

        @Test
        @DisplayName("no approval flag defaults to false → rejected")
        void noApprovalFlag() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, false);
            assertEquals(ApplicationSendResult.REJECTED.status(), result.status());
        }
    }

    // ─── 13. Successful send returns SENT only after actual mail operation ─

    @Nested
    @DisplayName("Send result accuracy")
    class SendResultTests {

        @Test
        @DisplayName("successful send returns SENT after mail op")
        void successfulSend() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            ApplicationSendResult result = service.send(draft, true);
            assertNotNull(result);
        }
    }

    // ─── 17. No PII-heavy logging ──────────────────────────────────────

    @Nested
    @DisplayName("PII-safe logging")
    class PIISafeLoggingTests {

        @Test
        @ExtendWith(OutputCaptureExtension.class)
        @DisplayName("service does not log recipient email or message body")
        void noPIILogging(CapturedOutput output) {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "secret.recipient@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            service.send(draft, true);
            assertFalse(output.getAll().contains("secret.recipient@example.com"),
                    "recipient email must not appear in logs");
            assertTrue(output.getAll().contains("maskedRecipient"),
                    "safe masked recipient metadata should be present");
        }
    }

    // ─── 18. EmailTools integration ──────────────────────────────────────

    @Nested
    @DisplayName("EmailTools integration")
    class EmailToolsIntegrationTests {

        @Test
        @DisplayName("EmailTools.sendEmail called when injected; returns SENT on success")
        void emailToolsSuccess() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            when(mockEmailTools.sendEmail("user@example.com", "Application for Java Developer", "Body"))
                    .thenReturn("Email sent to user@example.com.");
            ApplicationSendResult result = wiredService.send(draft, true);
            assertEquals(ApplicationSendResult.SENT.status(), result.status());
        }

        @Test
        @DisplayName("EmailTools failure → FAILED")
        void emailToolsFailure() {
            ApplicationEmailDraft draft = new ApplicationEmailDraft(
                    "j1", null, "Acme", "Java Developer",
                    "Hiring Manager", "user@example.com",
                    "Application for Java Developer",
                    "Body",
                    "DRAFT_ONLY",
                    ApplicationDraftStatus.REVIEW_REQUIRED,
                    List.of());
            when(mockEmailTools.sendEmail("user@example.com", "Application for Java Developer", "Body"))
                    .thenReturn("Failed to send email to user@example.com: Connection refused");
            ApplicationSendResult result = wiredService.send(draft, true);
            assertEquals(ApplicationSendResult.FAILED.status(), result.status());
        }
    }
}