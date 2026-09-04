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

    public ApplicationEmailService emailService() {
        return emailService;
    }

    public ApplicationPreparationService prepService() {
        return prepService;
    }

    public ApplicationEmailController(ApplicationEmailService emailService,
                                      ApplicationPreparationService prepService) {
        this.emailService = emailService;
        this.prepService = prepService;
    }

    /**
     * Sends an application email after explicit user approval.
     *
     * <p>The request body should contain an {@code approved} field.
     * <pre>
     * { "approved": true }
     * </pre>
     * Setting {@code approved} to {@code false} or omitting it will reject the send.
     *
     * @param request the approval request containing the {@code approved} flag
     * @return {@link ResponseEntity} with {@link ApplicationSendResult} and appropriate HTTP status
     */
    @PostMapping("/send")
    public ResponseEntity<ApplicationSendResult> send(@RequestBody Optional<ApprovalRequest> requestOpt) {
        boolean approved = requestOpt.map(ApprovalRequest::isApproved).orElse(false);

        // Build a default draft using the preparation service.
        // If preparation fails (e.g. null inputs), the resulting draft will
        // still be null-safe because ApplicationEmailService.send() handles null.
        ApplicationEmailDraft draft = prepService.prepare(null, null, null);

        ApplicationSendResult result = emailService.send(draft, approved);

        if (ApplicationSendResult.REJECTED.equals(result)
                || ApplicationSendResult.FAILED.equals(result)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /** Simple request bean for the approval flag. */
    public static class ApprovalRequest {
        @NotNull(message = "Approval flag is required")
        private Boolean approved;

        public ApprovalRequest() {
        }

        public ApprovalRequest(Boolean approved) {
            this.approved = approved;
        }

        public Boolean isApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }
    }
}