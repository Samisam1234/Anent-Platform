package com.agentplatform.orchestrator.tailoring;

import java.util.List;

/**
 * Deterministic, explainable ATS resume tailoring analysis for an existing
 * {@code CandidateProfile} toward a target {@code Job}.
 *
 * <p>The analysis only re-organizes and highlights content that already exists in the
 * candidate profile; it never fabricates skills, experience, projects, internships,
 * certifications or years. Missing required/preferred skills are surfaced as clearly
 * labelled gaps and never appear among highlighted or verified skills.</p>
 *
 * <p>This is analysis only — no new resume is generated and the original candidate
 * data is never modified.</p>
 */
public record ResumeTailoringAnalysis(
        Long candidateId,
        String jobId,
        List<String> matchedRequiredSkills,
        List<String> matchedPreferredSkills,
        List<String> missingRequiredSkills,
        List<String> missingPreferredSkills,
        List<HighlightedSkill> highlightedSkills,
        List<RelevantEntry> relevantProjects,
        List<RelevantEntry> relevantExperience,
        List<RelevantEntry> relevantInternships,
        List<ResumeSection> recommendedSectionOrder,
        List<TailoringRecommendation> tailoringRecommendations,
        List<TailoringRecommendation> missingRequirements,
        AtsReadinessAnalysis atsReadiness
) {
    public ResumeTailoringAnalysis {
        matchedRequiredSkills = defensivelyCopyStrings(matchedRequiredSkills);
        matchedPreferredSkills = defensivelyCopyStrings(matchedPreferredSkills);
        missingRequiredSkills = defensivelyCopyStrings(missingRequiredSkills);
        missingPreferredSkills = defensivelyCopyStrings(missingPreferredSkills);
        highlightedSkills = List.copyOf(highlightedSkills);
        relevantProjects = List.copyOf(relevantProjects);
        relevantExperience = List.copyOf(relevantExperience);
        relevantInternships = List.copyOf(relevantInternships);
        recommendedSectionOrder = List.copyOf(recommendedSectionOrder);
        tailoringRecommendations = List.copyOf(tailoringRecommendations);
        missingRequirements = List.copyOf(missingRequirements);
        atsReadiness = atsReadiness == null
                ? new AtsReadinessAnalysis(0, "", 0, 0, 0, 0, false, false, "")
                : atsReadiness;
    }

    private static List<String> defensivelyCopyStrings(List<String> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}