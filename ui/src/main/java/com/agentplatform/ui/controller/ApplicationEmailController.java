package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.application.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
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

        // Build draft from stored application
        ApplicationEmailDraft draft = buildDraftFromApplication(app);

        ApplicationSendResult result = emailService.send(draft, true);

        if (ApplicationSendResult.REJECTED.equals(result)
                || ApplicationSendResult.FAILED.equals(result)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Builds an email draft from a stored JobApplication.
     */
private ApplicationEmailDraft buildDraftFromApplication(JobApplication app) {
        // Use a default recipient name; email must be provided by user in real scenario
        // For testing/simulation purposes, we use a placeholder email with valid TLD
        String recipientName = "Hiring Manager";
        String recipientEmail = "hiring@company.com"; // Placeholder for simulation

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

        return new ApplicationEmailDraft(
                app.getJobId(),
                app.getCandidateId(),
                app.getCompany(),
                app.getJobTitle(),
                recipientName,
                recipientEmail,
                subject,
                body.toString(),
                "DRAFT_ONLY",
                ApplicationDraftStatus.READY_TO_SEND,
                java.util.List.of(
                        "This application has been approved for submission.",
                        "Review all information before sending.",
                        "Recipient email must be entered or verified by the user."
                )
        );
    }

    /** Request payload for sending an application email. */
    public static class SendRequest {
        @NotNull(message = "Application ID is required")
        private Long applicationId;

        @NotNull(message = "Approval flag is required")
        private Boolean approved;

        public SendRequest() {
        }

        public SendRequest(Long applicationId, Boolean approved) {
            this.applicationId = applicationId;
            this.approved = approved;
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
    }
}