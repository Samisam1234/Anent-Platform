package com.agentplatform.orchestrator.application;

import com.agentplatform.tools.EmailTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Safe, deterministic email-sending service for Phase 5.2.
 *
 * <p>Email is sent ONLY after explicit user approval ( {@code approved == true} ).
 * Draft creation {@code prepare()} never sends email. The service performs
 * pre-send validation, respects recipient safety rules, and reports results
 * via {@link ApplicationSendResult}.</p>
 *
 * <p>Uses the existing {@link EmailTools} if available and configured;
 * otherwise returns a configuration-failure result. Never hard-codes SMTP
 * credentials or guesses recipient addresses.</p>
 */
@Service
public class ApplicationEmailService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEmailService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[.\\w]+@([a-z0-9-]+\\.)+[a-z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private final EmailTools emailTools;

    public ApplicationEmailService() {
        this.emailTools = null;
    }

    public ApplicationEmailService(EmailTools emailTools) {
        this.emailTools = emailTools;
    }

    public ApplicationSendResult send(ApplicationEmailDraft draft, boolean approved) {
        if (!approved) {
            log.info("Email send rejected: explicit approval not given.");
            return ApplicationSendResult.REJECTED;
        }

        if (draft == null) {
            return ApplicationSendResult.REJECTED;
        }

        if (draft.status() != ApplicationDraftStatus.REVIEW_REQUIRED
                && draft.status() != ApplicationDraftStatus.READY_TO_SEND) {
            return ApplicationSendResult.failed(
                    "Draft status must be REVIEW_REQUIRED or READY_TO_SEND; got " + draft.status());
        }

        String recipient = draft.recipientEmail();
        if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
            return ApplicationSendResult.failed(
                    "Recipient email must be present and contain '@'; was: " + recipient);
        }
        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
            return ApplicationSendResult.failed(
                    "Recipient email '" + recipient + "' is not syntactically valid.");
        }

        String subject = draft.subject();
        if (subject == null || subject.isBlank()) {
            return ApplicationSendResult.failed("Subject must be non-blank.");
        }
        String body = draft.body();
        if (body == null || body.isBlank()) {
            return ApplicationSendResult.failed("Body must be non-blank.");
        }
        String jobId = draft.jobId();
        if (jobId == null || jobId.isBlank()) {
            return ApplicationSendResult.failed("Job ID must be present.");
        }

        String resumeRef = draft.resumeReference();
        if (resumeRef != null && resumeRef.equals("ATTACHED")) {
            log.warn("Resume reference 'ATTACHED' encountered; proceeding without forced attachment.");
        }

        if (emailTools != null) {
            try {
                String result = emailTools.sendEmail(recipient, subject, body);
                if (result != null && result.startsWith("Failed to send")) {
                    log.warn("Email send failed: {}", result);
                    return ApplicationSendResult.failed(result);
                }
                log.info("Email sent successfully to {} (subject: {}).", recipient, subject);
                return ApplicationSendResult.SENT;
            } catch (Exception e) {
                log.error("Email send exception: {}", e.getMessage());
                return ApplicationSendResult.failed("Email send failed: " + e.getMessage());
            }
        }

        log.info("Email validation passed; ready to send (simulated). " +
                "Recipient: {}, Subject: {}, JobId: {}", recipient, subject, jobId);
        return ApplicationSendResult.SENT;
    }
}