package com.agentplatform.orchestrator.tailoring;

import com.agentplatform.orchestrator.resume.ResumeEvidence;

import java.util.List;

/**
 * A verified candidate skill that should be highlighted for the target job.
 *
 * <p>Only skills that matched the job (from the authoritative
 * {@code CareerGapAnalysis}) appear here. {@code evidenceSources} lists the resume
 * sections where the skill was actually observed; it is never fabricated, and missing
 * skills never appear in a highlighted result.</p>
 */
public record HighlightedSkill(
        String canonicalSkill,
        List<ResumeEvidence.SourceSection> evidenceSources
) {
    public HighlightedSkill {
        canonicalSkill = canonicalSkill == null ? "" : canonicalSkill.trim();
        evidenceSources = evidenceSources != null ? List.copyOf(evidenceSources) : List.of();
    }
}