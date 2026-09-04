package com.agentplatform.orchestrator.gap;

import com.agentplatform.orchestrator.resume.ResumeEvidence;

import java.util.List;

/**
 * A single canonicalized skill and, where deterministically known, the resume
 * sections that evidence it for the candidate.
 *
 * <p>For a <em>matched</em> skill {@code evidenceSources} lists the resume sections
 * (e.g. PROJECT, SKILLS) where the canonical skill was observed, so the match is
 * explainable. For a <em>missing</em> skill the list is empty — no evidence is
 * fabricated.</p>
 */
public record SkillGap(
        String canonicalSkill,
        List<ResumeEvidence.SourceSection> evidenceSources
) {
    public SkillGap {
        canonicalSkill = canonicalSkill == null ? "" : canonicalSkill.trim();
        evidenceSources = evidenceSources != null ? List.copyOf(evidenceSources) : List.of();
    }
}