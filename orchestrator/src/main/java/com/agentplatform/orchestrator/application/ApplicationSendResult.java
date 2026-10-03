package com.agentplatform.orchestrator.application;

/**
 * Result returned from attempting to send an {@link ApplicationEmailDraft}.
 *
 * <p>Phase 5.2 defers the actual send until the caller explicitly approves.
 * Until then, the result status will be {@link #REJECTED} or
 * {@link #REVIEW_REQUIRED}.</p>
 */
public record ApplicationSendResult(
        String status,
        String message,
        String jobId,
        String recipientEmail
) {
    public ApplicationSendResult {
        status = status == null ? "" : status.trim();
        message = message == null ? "" : message.trim();
        jobId = jobId == null ? "" : jobId.trim();
        recipientEmail = recipientEmail == null ? "" : recipientEmail.trim();
    }

    /** Sent successfully via SMTP. */
    public static final ApplicationSendResult SENT =
            new ApplicationSendResult("SENT", "Application email sent successfully.", "", "");

    /** Explicit user approval was not given. */
    public static final ApplicationSendResult REJECTED =
            new ApplicationSendResult("REJECTED", "Explicit user approval is required before sending.", "", "");

    /** Validation failed or send could not proceed. */
    public static final ApplicationSendResult FAILED =
            new ApplicationSendResult("FAILED", "Email could not be sent due to validation or configuration errors.", "", "");

    /** Builder-style factory for common cases. */
    public static ApplicationSendResult rejected(String reason) {
        return new ApplicationSendResult("REJECTED", reason, "", "");
    }

    public static ApplicationSendResult failed(String reason) {
        return new ApplicationSendResult("FAILED", reason, "", "");
    }
}