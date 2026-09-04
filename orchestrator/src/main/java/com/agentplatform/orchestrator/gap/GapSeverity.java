package com.agentplatform.orchestrator.gap;

/**
 * Deterministic overall severity of a career-skill gap for a candidate versus a job.
 *
 * <p>Transparent mapping (see {@link CareerGapAnalysisService}): missing required
 * skills weigh more than missing preferred skills, with modest contributions from an
 * experience shortfall and a career-track mismatch. No LLM is involved.</p>
 */
public enum GapSeverity {
    NO_GAP,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}