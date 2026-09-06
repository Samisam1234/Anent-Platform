package com.agentplatform.orchestrator.advisor;

/**
 * Controlled recommendation for the Application Advisor.
 *
 * <p>These values represent the authoritative, deterministic recommendation
 * that the Application Advisor may return. The LLM must never invent arbitrary
 * recommendation labels — this enum is the single source of truth.</p>
 */
public enum ApplicationRecommendation {

    /** Candidate is an excellent fit; strongly recommended to apply. */
    STRONGLY_RECOMMENDED,

    /** Candidate is a good fit; recommended to apply. */
    RECOMMENDED,

    /** Candidate has potential but should improve specific areas before applying. */
    APPLY_WITH_IMPROVEMENTS,

    /** Candidate may apply but it is a lower priority compared to other opportunities. */
    LOW_PRIORITY,

    /** Candidate is not a fit; not recommended to apply. */
    NOT_RECOMMENDED
}
