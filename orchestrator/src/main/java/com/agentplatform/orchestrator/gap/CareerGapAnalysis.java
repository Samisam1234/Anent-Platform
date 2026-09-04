package com.agentplatform.orchestrator.gap;

import com.agentplatform.orchestrator.matching.CareerTrack;

import java.util.List;

/**
 * Deterministic career-gap analysis comparing a {@code CandidateProfile} against a
 * {@code Job}.
 *
 * <p>Reproducible and explainable: every matched/missing skill is canonicalized, every
 * matched skill carries candidate evidence sources, the experience gap is only filled
 * when structured years exist on both sides, and the overall severity follows a
 * documented scoring rule (see {@link CareerGapAnalysisService}).</p>
 */
public record CareerGapAnalysis(
        Long candidateId,
        String jobId,
        List<SkillGap> matchedRequiredSkills,
        List<SkillGap> missingRequiredSkills,
        List<SkillGap> matchedPreferredSkills,
        List<SkillGap> missingPreferredSkills,
        ExperienceGap experienceGap,
        CareerTrack candidateTrack,
        CareerTrack jobTrack,
        boolean trackMismatch,
        GapSeverity overallGapSeverity,
        List<ImprovementPriority> improvementPriorities
) {
    public CareerGapAnalysis {
        matchedRequiredSkills = dedup(matchedRequiredSkills);
        missingRequiredSkills = dedup(missingRequiredSkills);
        matchedPreferredSkills = dedup(matchedPreferredSkills);
        missingPreferredSkills = dedup(missingPreferredSkills);
        improvementPriorities = improvementPriorities != null ? List.copyOf(improvementPriorities) : List.of();
    }

    private static List<SkillGap> dedup(List<SkillGap> list) {
        if (list == null) {
            return List.of();
        }
        // Keep the first occurrence of each canonical skill (deterministic de-duplication).
        return java.util.List.copyOf(
                list.stream().collect(java.util.stream.Collectors.toMap(
                        SkillGap::canonicalSkill, s -> s, (a, b) -> a, java.util.LinkedHashMap::new))
                        .values().stream().toList());
    }
}