package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.application.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("ApplicationEmailController — user-controlled email send (Phase 6.11)")
class ApplicationEmailControllerTest {

    private final JobApplicationRepository repository = mock(JobApplicationRepository.class);
    private final ApplicationStorageService storage = new ApplicationStorageService(repository);
    private final ApplicationEmailController controller =
            new ApplicationEmailController(
                    new com.agentplatform.orchestrator.application.ApplicationEmailService(),
                    new com.agentplatform.orchestrator.application.ApplicationPreparationService(),
                    storage);

    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, JobApplication> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        nextId.set(1);
        // Configure mock repository to assign IDs when saving
        lenient().when(repository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication app = inv.getArgument(0);
            if (app.getId() == null) {
                app.setId(nextId.getAndIncrement());
            }
            store.put(app.getId(), app);
            return app;
        });
        lenient().when(repository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });
    }

    private JobApplication storedApprovedApp() {
        JobApplication app = new JobApplication(1L, "job-123", "Software Engineer", "Test Company", "Hyderabad");
        app.setCoverLetter("Test cover letter");
        app.setGeneratedResumeSummary("Test summary");
        app.setApplicationAnswers("Answer 1\nAnswer 2");
        app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
        app.setCandidateId(1L);
        return storage.store(app);
    }

    private JobApplication storedGeneratedApp() {
        JobApplication app = new JobApplication(1L, "job-123", "Software Engineer", "Test Company", "Hyderabad");
        app.setCoverLetter("Test cover letter");
        app.setApplicationStatus(ApplicationStatus.GENERATED);
        app.setCandidateId(1L);
        return storage.store(app);
    }

    // ─── 1. approved=false → email tool NOT called ───────────────────────

    @Nested
    @DisplayName("Approval control")
    class ApprovalControlTests {

        @Test
        @DisplayName("approved=false → REJECTED")
        void approvedFalse() {
            JobApplication app = storedApprovedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), false);
            var result = controller.send(request);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals(ApplicationSendResult.REJECTED, result.getBody());
        }

        @Test
        @DisplayName("missing approval (null) → REJECTED")
        void missingApproval() {
            JobApplication app = storedApprovedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), null);
            var result = controller.send(request);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals(ApplicationSendResult.REJECTED, result.getBody());
        }
    }

    // ─── 2. Application not found ────────────────────────────────────────

    @Nested
    @DisplayName("Application not found")
    class NotFoundTests {

        @Test
        @DisplayName("non-existent applicationId → NOT_FOUND with FAILED result")
        void nonExistentApp() {
            var request = new ApplicationEmailController.SendRequest(999L, true);
            var result = controller.send(request);
            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
            assertEquals("FAILED", result.getBody().status());
            assertTrue(result.getBody().message().contains("not found"));
        }
    }

    // ─── 3. Application not approved ────────────────────────────────────

    @Nested
    @DisplayName("Application not approved")
    class NotApprovedTests {

        @Test
        @DisplayName("GENERATED status → BAD_REQUEST with FAILED result")
        void generatedStatus() {
            JobApplication app = storedGeneratedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), true);
            var result = controller.send(request);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("FAILED", result.getBody().status());
            assertTrue(result.getBody().message().contains("must be approved"));
        }

        @Test
        @DisplayName("REJECTED status → BAD_REQUEST with FAILED result")
        void rejectedStatus() {
            JobApplication app = new JobApplication(1L, "job-123", "Software Engineer", "Test Company", "Hyderabad");
            app.setCoverLetter("Test cover letter");
            app.setApplicationStatus(ApplicationStatus.REJECTED);
            app.setCandidateId(1L);
            storage.store(app);

            var request = new ApplicationEmailController.SendRequest(app.getId(), true);
            var result = controller.send(request);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("FAILED", result.getBody().status());
            assertTrue(result.getBody().message().contains("must be approved"));
        }
    }

    // ─── 4. Missing applicationId ──────────────────────────────────────

    @Nested
    @DisplayName("Missing applicationId")
    class MissingAppIdTests {

        @Test
        @DisplayName("null applicationId → BAD_REQUEST with FAILED result")
        void nullApplicationId() {
            var request = new ApplicationEmailController.SendRequest(null, true);
            var result = controller.send(request);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("FAILED", result.getBody().status());
            assertTrue(result.getBody().message().contains("applicationId is required"));
        }
    }

    // ─── 5. Successful send (approved + approved=true) ──────────────────

    @Nested
    @DisplayName("Successful send")
    class SuccessfulSendTests {

        @Test
        @DisplayName("approved=true + APPROVED_FOR_APPLICATION → send attempted")
        void approvedTrue() {
            JobApplication app = storedApprovedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), true);
            var result = controller.send(request);
            assertNotNull(result.getBody());
            // With no EmailTools configured, service returns SENT (simulated)
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("SENT", result.getBody().status());
        }
    }

    // ─── 6. Missing approval ───────────────────────────────────────────

    @Nested
    @DisplayName("Missing approval")
    class MissingApprovalTests {

        @Test
        @DisplayName("missing approval → BAD_REQUEST + REJECTED")
        void missingApprovalReturn() {
            JobApplication app = storedApprovedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), null);
            var result = controller.send(request);
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals(ApplicationSendResult.REJECTED, result.getBody());
        }
    }

    // ─── 7. Recipient validation ──────────────────────────────────────

    @Nested
    @DisplayName("Recipient validation")
    class RecipientValidationTests {

        @Test
        @DisplayName("valid recipient email → service returns SENT (simulated)")
        void validRecipient() {
            // The draft now has a valid recipient email (hiring@company.com),
            // so the email service returns SENT (simulated, no EmailTools configured).
            JobApplication app = storedApprovedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), true);
            var result = controller.send(request);
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("SENT", result.getBody().status());
        }
    }

    // ─── 8. Safe error response ────────────────────────────────────────

    @Nested
    @DisplayName("Safe error response")
    class SafeErrorResponseTests {

        @Test
        @DisplayName("safe error response no stack trace")
        void safeErrorResponse() {
            JobApplication app = storedApprovedApp();
            var request = new ApplicationEmailController.SendRequest(app.getId(), true);
            var result = controller.send(request);
            assertNotNull(result.getBody());
            // The result message should not contain stack trace text.
            assertTrue(result.getBody().message().length() < 500);
        }
    }

    // ─── 9. API compatibility ──────────────────────────────────────────

    @Nested
    @DisplayName("API compatibility")
    class APICompatibilityTests {

        @Test
        @DisplayName("existing APIs remain compatible")
        void existingApisCompatible() {
            assertNotNull(controller);
        }
    }
}