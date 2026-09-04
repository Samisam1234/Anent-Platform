package com.agentplatform.orchestrator.agent;

/**
 * Logical agent types in the career orchestration pipeline.
 *
 * <p>These are NOT independent LLM runtimes — they are logical components that
 * delegate to existing deterministic services. Each maps to a bounded stage of
 * the career workflow.</p>
 */
public enum AgentType {

    /** Parses and structures a candidate resume profile. */
    RESUME,

    /** Discovers jobs via the existing job search service. */
    JOB_DISCOVERY,

    /** Matches a candidate against discovered jobs. */
    MATCHING,

    /** Produces career-gap analysis and improvement guidance. */
    CAREER_ADVISOR,

    /** Produces ATS tailoring analysis, tailored draft, and application draft. */
    APPLICATION_ADVISOR
}
