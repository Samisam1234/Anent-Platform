package com.agentplatform.orchestrator.tailoring;

/**
 * How a {@link TailoredResumeDraft} was produced.
 *
 * <p>Phase 4.5 is fully deterministic: the draft is produced by rule-based
 * reordering/rewording of existing candidate content. No LLM, no external service.
 * A future phase may add an {@code AI_REWRITTEN} origin, but the safe,
 * reproducible path starts at {@link #DETERMINISTIC}.</p>
 */
public enum DraftOrigin {
    /** Produced purely by deterministic, local rules over existing candidate content. */
    DETERMINISTIC
}