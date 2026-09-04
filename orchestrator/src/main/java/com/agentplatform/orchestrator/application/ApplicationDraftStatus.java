package com.agentplatform.orchestrator.application;

/**
 * Status of a job application email draft.
 *
 * <p>Phase 5.1 always sets the draft status to {@link #REVIEW_REQUIRED}
 * to enforce that a human reviews the application before any sending action.</p>
 */
public enum ApplicationDraftStatus {
    /** The draft has been prepared and requires user review before sending. */
    REVIEW_REQUIRED,

    /** The draft is ready for user review (an intermediate state). */
    DRAFT,

    /** The draft has been reviewed and is ready to be sent by the user. */
    READY_TO_SEND
}