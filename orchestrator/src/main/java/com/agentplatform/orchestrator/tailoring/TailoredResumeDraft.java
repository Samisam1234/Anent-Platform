package com.agentplatform.orchestrator.tailoring;

import java.util.List;

/**
 * A review-only, safe tailored resume draft produced for a candidate and a target job.
 *
 * <p>The draft is derived purely from existing {@code CandidateProfile} content plus the
 * {@link ResumeTailoringAnalysis}. It only reorders, rewrites-for-clarity, and highlights
 * existing information — it never adds missing skills, invents experience/projects/
 * internships/certifications/years, and never mutates the original candidate data.</p>
 *
 * <p>This is a draft for human review; it is not persisted and does not overwrite the
 * original resume, the {@code CandidateProfile}, or any database record.</p>
 */
public record TailoredResumeDraft(
        String jobId,
        Long candidateId,
        String professionalSummary,
        List<String> orderedSkills,
        List<String> highlightedProjects,
        List<String> highlightedExperience,
        List<String> highlightedInternships,
        List<ResumeSection> sectionOrder,
        DraftOrigin origin,
        List<String> warnings
) {
    public TailoredResumeDraft {
        professionalSummary = professionalSummary == null ? "" : professionalSummary.trim();
        orderedSkills = copyStrings(orderedSkills);
        highlightedProjects = copyStrings(highlightedProjects);
        highlightedExperience = copyStrings(highlightedExperience);
        highlightedInternships = copyStrings(highlightedInternships);
        sectionOrder = sectionOrder != null ? List.copyOf(sectionOrder) : List.of();
        origin = origin == null ? DraftOrigin.DETERMINISTIC : origin;
        warnings = copyStrings(warnings);
    }

    private static List<String> copyStrings(List<String> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}