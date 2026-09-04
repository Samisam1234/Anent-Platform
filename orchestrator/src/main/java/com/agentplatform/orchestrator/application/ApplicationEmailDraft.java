package com.agentplatform.orchestrator.application;

import java.util.List;

/**
 * A safe, immutable email draft for a job application — review-only, never auto-sent.
 *
 * <p>This draft is generated deterministically from existing candidate and job information.
 * It never contacts external services, never sends email, and only uses information that
 * is already verified and available in the system.</p>
 *
 * <p>The {@link #status} is always {@link ApplicationDraftStatus#REVIEW_REQUIRED} in
 * Phase 5.1 — the user must review and explicitly send any application.</p>
 */
public record ApplicationEmailDraft(
        String jobId,
        Long candidateId,
        String company,
        String jobTitle,
        String recipientName,
        String recipientEmail,
        String subject,
        String body,
        String resumeReference,
        ApplicationDraftStatus status,
        List<String> warnings
) {
    public ApplicationEmailDraft {
        jobId = jobId == null ? "" : jobId.trim();
        candidateId = candidateId != null ? candidateId : null;
        company = company == null ? "" : company.trim();
        jobTitle = jobTitle == null ? "" : jobTitle.trim();
        recipientName = recipientName == null ? "Hiring Manager" : recipientName.trim();
        recipientEmail = recipientEmail == null ? null : recipientEmail.trim();
        subject = subject == null ? "" : subject.trim();
        body = body == null ? "" : body.trim();
        resumeReference = resumeReference == null ? "DRAFT_ONLY" : resumeReference.trim();
        status = status == null ? ApplicationDraftStatus.REVIEW_REQUIRED : status;
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}