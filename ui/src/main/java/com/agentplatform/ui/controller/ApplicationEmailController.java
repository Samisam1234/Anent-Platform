package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.application.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/applications/email")
public class ApplicationEmailController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEmailController.class);

    private final ApplicationEmailService emailService;
    private final ApplicationPreparationService prepService;
    private final ApplicationStorageService storageService;

    public ApplicationEmailController(ApplicationEmailService emailService,
                                      ApplicationPreparationService prepService,
                                      ApplicationStorageService storageService) {
        this.emailService = emailService;
        this.prepService = prepService;
        this.storageService = storageService;
    }

    /**
     * Sends an application email after explicit user approval.
     *
     * <p>The request body should contain an {@code applicationId} and {@code approved} field.
     * <pre>
     * { "applicationId": 1, "approved": true }
     * </pre>
     * Setting {@code approved} to {@code false} or omitting it will reject the send.
     *
     * @param request the approval request containing the applicationId and approved flag
     * @return {@link ResponseEntity} with {@link ApplicationSendResult} and appropriate HTTP status
     */
    @PostMapping("/send")
    public ResponseEntity<ApplicationSendResult> send(@RequestBody SendRequest request) {
        if (request == null || request.getApplicationId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApplicationSendResult.failed("applicationId is required"));
        }

        // Handle null approved (default to false)
        boolean approved = Boolean.TRUE.equals(request.isApproved());

        // Fetch the stored application
        Optional<JobApplication> appOpt = storageService.findById(request.getApplicationId());
        if (appOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApplicationSendResult.failed("Application not found: " + request.getApplicationId()));
        }

        JobApplication app = appOpt.get();

        // Terminal EMAIL_SENT: the package email was already sent via a real transport;
        // no transport invocation is repeated.
        if (app.getApplicationStatus() == ApplicationStatus.EMAIL_SENT) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApplicationSendResult.failed(
                            "Email already sent for application " + request.getApplicationId() + "."));
        }

        // Check if application is approved for sending
        if (app.getApplicationStatus() != ApplicationStatus.APPROVED_FOR_APPLICATION) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApplicationSendResult.failed(
                            "Application must be approved before sending. Current status: " + app.getApplicationStatus()));
        }

        if (!approved) {
            log.info("Email send rejected: explicit approval not given for application {}", request.getApplicationId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApplicationSendResult.REJECTED);
        }

        // A recipientEmail that is present must be syntactically valid — the send
        // service is never invoked for a malformed address.
        String requestedRecipient = request.getRecipientEmail();
        if (requestedRecipient != null && !requestedRecipient.isBlank()
                && !ApplicationEmailService.isValidRecipient(requestedRecipient)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApplicationSendResult.failed(
                            "Recipient email '" + requestedRecipient + "' is not syntactically valid."));
        }

        // Build draft from stored application; an absent/invalid recipient keeps the
        // draft in REVIEW_REQUIRED so the placeholder is never substituted as a real
        // send address.
        ApplicationEmailDraft draft = buildDraftFromApplication(app, requestedRecipient);

        ApplicationSendResult result = emailService.send(draft, true);

        // Compare by status string: the FAILED/REJECTED constants carry generic
        // messages while actual results carry the specific reason.
        String status = result == null ? null : result.status();
        if (ApplicationSendResult.REJECTED.status().equals(status)
                || ApplicationSendResult.FAILED.status().equals(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // Accepted send (result SENT — real or simulated): persist the outcome and,
        // for a real send, transition to EMAIL_SENT. Transport runs before this,
        // persistence after; a crash between them loses only this record (see
        // ApplicationStorageService.recordEmailSendOutcome) — never reported as delivery.
        Optional<JobApplication> updated = storageService.recordEmailSendOutcome(
                request.getApplicationId(), result.simulated());
        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApplicationSendResult.failed("Application not found: " + request.getApplicationId()));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Builds an email draft from a stored JobApplication.
     *
     * <p>A non-blank, syntactically valid {@code recipientEmail} becomes the real
     * recipient (draft {@code READY_TO_SEND}, no placeholder warning). When absent,
     * the draft stays {@code REVIEW_REQUIRED} with the placeholder warning and a
     * {@code null} recipient — the placeholder is never used as a send address.</p>
     */
private ApplicationEmailDraft buildDraftFromApplication(JobApplication app, String recipientEmail) {
        boolean hasRecipient = recipientEmail != null && !recipientEmail.isBlank();
        String recipientName = "Hiring Manager";
        String recipient = hasRecipient ? recipientEmail.trim() : null;

        String subject = app.getCoverLetter() != null && !app.getCoverLetter().isBlank()
                ? app.getCoverLetter().split("\n")[0] // First line as subject fallback
                : "Application for " + app.getJobTitle();

        // If we have a proper subject in the cover letter, extract it
        if (app.getCoverLetter() != null && app.getCoverLetter().contains("Subject:")) {
            String[] lines = app.getCoverLetter().split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("Subject:")) {
                    subject = line.substring("Subject:".length()).trim();
                    break;
                }
            }
        }

        // Build a more complete body from the application
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(recipientName).append(",\n\n");
        body.append(app.getCoverLetter() != null ? app.getCoverLetter() : "Cover letter not available.").append("\n\n");
        body.append("Sincerely,\n");
        body.append(app.getCandidateId() != null ? "Candidate ID: " + app.getCandidateId() : "Applicant");

        List<String> warnings = new ArrayList<>(List.of(
                "This application has been approved for submission.",
                "Review all information before sending."
        ));
        ApplicationDraftStatus status = ApplicationDraftStatus.READY_TO_SEND;
        if (!hasRecipient) {
            status = ApplicationDraftStatus.REVIEW_REQUIRED;
            warnings.add("Recipient email must be entered or verified by the user.");
        }

        return new ApplicationEmailDraft(
                app.getJobId(),
                app.getCandidateId(),
                app.getCompany(),
                app.getJobTitle(),
                recipientName,
                recipient,
                subject,
                body.toString(),
                "DRAFT_ONLY",
                status,
                warnings
        );
    }

    /** Request payload for sending an application email. */
    public static class SendRequest {
        @NotNull(message = "Application ID is required")
        private Long applicationId;

        @NotNull(message = "Approval flag is required")
        private Boolean approved;

        /** Optional user-verified recipient email; when absent the send stays REVIEW_REQUIRED. */
        private String recipientEmail;

        public SendRequest() {
        }

        public SendRequest(Long applicationId, Boolean approved) {
            this(applicationId, approved, null);
        }

        public SendRequest(Long applicationId, Boolean approved, String recipientEmail) {
            this.applicationId = applicationId;
            this.approved = approved;
            this.recipientEmail = recipientEmail;
        }

        public Long getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(Long applicationId) {
            this.applicationId = applicationId;
        }

        public Boolean isApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }

        public String getRecipientEmail() {
            return recipientEmail;
        }

        public void setRecipientEmail(String recipientEmail) {
            this.recipientEmail = recipientEmail;
        }
    }
}