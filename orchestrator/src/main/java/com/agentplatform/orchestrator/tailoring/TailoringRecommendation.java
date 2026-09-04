package com.agentplatform.orchestrator.tailoring;

import com.agentplatform.orchestrator.resume.ResumeEvidence;

import java.util.List;

/**
 * A single deterministic, explainable recommendation for tailoring an existing
 * resume toward a target job.
 *
 * <p>{@code focus} names the subject (a canonical skill, a project/experience line, a
 * resume section, or a missing required skill). {@code reason} explains the relevance
 * without inventing anything, and {@code evidenceSources} lists the resume sections
 * supporting the claim where available (empty for missing items marked
 * {@link RecommendationType#MISSING_REQUIREMENT}).</p>
 */
public record TailoringRecommendation(
        RecommendationType type,
        String focus,
        String reason,
        List<ResumeEvidence.SourceSection> evidenceSources
) {
    public TailoringRecommendation {
        focus = focus == null ? "" : focus.trim();
        reason = reason == null ? "" : reason.trim();
        evidenceSources = evidenceSources != null ? List.copyOf(evidenceSources) : List.of();
    }
}