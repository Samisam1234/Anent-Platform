package com.agentplatform.orchestrator.tailoring;

/**
 * The category of a {@link TailoringRecommendation}.
 *
 * <p>Every recommendation is deterministic and grounded in either verified candidate
 * evidence or an authoritative {@code CareerGapAnalysis} missing-skill result. The
 * system never fabricates candidate content.</p>
 */
public enum RecommendationType {
    /** Highlight an existing, verified candidate skill that matches the job. */
    HIGHLIGHT_SKILL,
    /** Highlight an existing project whose text shows deterministic relevance. */
    HIGHLIGHT_PROJECT,
    /** Highlight an existing experience entry with deterministic relevance. */
    HIGHLIGHT_EXPERIENCE,
    /** Highlight an existing internship entry with deterministic relevance. */
    HIGHLIGHT_INTERNSHIP,
    /** Recommend prioritizing a resume section in the suggested order. */
    SECTION_PRIORITY,
    /** Signal a requirement the job asks for that the candidate does not have. */
    MISSING_REQUIREMENT
}