package com.agentplatform.orchestrator.advisor;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;

import java.util.List;

/**
 * Deterministic, per-factor breakdown behind the Application Advisor's readiness
 * score, so the UI can show <em>why</em> a recommendation was produced instead of a
 * bare number.
 *
 * <p>Every field is copied from an existing deterministic result — nothing here is
 * estimated, randomised or AI-generated:</p>
 * <ul>
 *   <li>{@code atsReadinessScore} and the evidence flags come from
 *       {@link AtsReadinessAnalysis}.</li>
 *   <li>The six {@code *FitScore} values are {@link JobMatch}'s 0.0–1.0 factor scores
 *       converted to 0–100. They are the same factors
 *       {@code JobMatchingService} already weights into {@code matchScore}.</li>
 *   <li>Skill counts, the experience gap, career tracks and gap severity come from
 *       {@link CareerGapAnalysis}.</li>
 * </ul>
 *
 * <p>{@code experienceKnowable == false} means the years comparison could not be made
 * from structured data; in that case {@code requiredYears} and/or
 * {@code candidateYears} is {@code null} and no experience shortfall is claimed.</p>
 *
 * <p>{@link #from(AtsReadinessAnalysis, JobMatch, CareerGapAnalysis)} tolerates a
 * missing {@link JobMatch} (no match could be computed for the job) by returning
 * zeros for the fit scores — an absent factor is reported as absent, never guessed.</p>
 */
public record AdvisorScoreBreakdown(
        /** Deterministic ATS readiness score (0–100), the 60% component of readiness. */
        int atsReadinessScore,
        /** Job match factor: required + preferred skill coverage (0–100). */
        int skillFitScore,
        /** Job match factor: job title vs candidate role/skills (0–100). */
        int roleFitScore,
        /** Job match factor: years of experience (0–100). */
        int experienceFitScore,
        /** Job match factor: education requirement (0–100). */
        int educationFitScore,
        /** Job match factor: location (0–100). */
        int locationFitScore,
        /** Job match factor: career track alignment (0–100). */
        int trackFitScore,
        int matchedRequiredCount,
        int missingRequiredCount,
        int matchedPreferredCount,
        int missingPreferredCount,
        /** Whether the resume contains project evidence relevant to this job. */
        boolean relevantProjectEvidence,
        /** Whether the resume contains experience evidence relevant to this job. */
        boolean relevantExperienceEvidence,
        /** False when the years comparison could not be derived from structured data. */
        boolean experienceKnowable,
        /** Years the job requires, or {@code null} when the requirement is unparseable. */
        Integer requiredYears,
        /** Candidate years from structured experience lines, or {@code null} if absent. */
        Integer candidateYears,
        String candidateTrack,
        String jobTrack,
        boolean trackMismatch,
        /** Name of {@code GapSeverity}, or {@code ""} when unknown. */
        String gapSeverity,
        /** Existing ATS explanation text, or {@code ""} when none was produced. */
        String atsExplanation,
        /** Canonical required skills the candidate demonstrably has. */
        List<String> matchedRequiredSkills,
        /** Canonical preferred skills the candidate demonstrably has. */
        List<String> matchedPreferredSkills,
        /** Canonical required skills absent from the candidate profile. */
        List<String> missingRequiredSkills,
        /** Canonical preferred skills absent from the candidate profile. */
        List<String> missingPreferredSkills
) {

    public AdvisorScoreBreakdown {
        atsReadinessScore = clamp(atsReadinessScore);
        skillFitScore = clamp(skillFitScore);
        roleFitScore = clamp(roleFitScore);
        experienceFitScore = clamp(experienceFitScore);
        educationFitScore = clamp(educationFitScore);
        locationFitScore = clamp(locationFitScore);
        trackFitScore = clamp(trackFitScore);
        candidateTrack = trim(candidateTrack);
        jobTrack = trim(jobTrack);
        gapSeverity = trim(gapSeverity);
        atsExplanation = trim(atsExplanation);
        matchedRequiredSkills = copy(matchedRequiredSkills);
        matchedPreferredSkills = copy(matchedPreferredSkills);
        missingRequiredSkills = copy(missingRequiredSkills);
        missingPreferredSkills = copy(missingPreferredSkills);
    }

    private static List<String> copy(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Converts a {@link JobMatch} factor score (0.0–1.0) to a 0–100 percentage. */
    private static int toPercent(double factorScore) {
        if (Double.isNaN(factorScore)) {
            return 0;
        }
        return clamp((int) Math.round(factorScore * 100.0));
    }

    /**
     * Builds the breakdown from the three deterministic inputs the advisor already
     * computes. Any of them may be {@code null}; absent data is reported as zero or
     * {@code null} rather than estimated.
     */
    public static AdvisorScoreBreakdown from(AtsReadinessAnalysis readiness,
                                            JobMatch jobMatch,
                                            CareerGapAnalysis gap) {
        int atsScore = readiness != null ? readiness.score() : 0;
        int matchedRequired = readiness != null ? readiness.matchedRequiredCount() : 0;
        int missingRequired = readiness != null ? readiness.missingRequiredCount() : 0;
        int matchedPreferred = readiness != null ? readiness.matchedPreferredCount() : 0;
        int missingPreferred = readiness != null ? readiness.missingPreferredCount() : 0;
        boolean projectEvidence = readiness != null && readiness.relevantProjectEvidence();
        boolean experienceEvidence = readiness != null && readiness.relevantExperienceEvidence();
        String atsExplanation = readiness != null ? readiness.explanation() : "";

        int skillFit = jobMatch != null ? toPercent(jobMatch.skillScore()) : 0;
        int roleFit = jobMatch != null ? toPercent(jobMatch.roleScore()) : 0;
        int experienceFit = jobMatch != null ? toPercent(jobMatch.experienceScore()) : 0;
        int educationFit = jobMatch != null ? toPercent(jobMatch.educationScore()) : 0;
        int locationFit = jobMatch != null ? toPercent(jobMatch.locationScore()) : 0;
        int trackFit = jobMatch != null ? toPercent(jobMatch.trackScore()) : 0;

        boolean knowable = false;
        Integer requiredYears = null;
        Integer candidateYears = null;
        String candidateTrack = "";
        String jobTrack = "";
        boolean trackMismatch = false;
        String gapSeverity = "";
        List<String> matchedRequiredSkills = List.of();
        List<String> matchedPreferredSkills = List.of();
        List<String> missingRequiredSkills = List.of();
        List<String> missingPreferredSkills = List.of();

        if (gap != null) {
            matchedRequiredSkills = canonicalNames(gap.matchedRequiredSkills());
            matchedPreferredSkills = canonicalNames(gap.matchedPreferredSkills());
            missingRequiredSkills = canonicalNames(gap.missingRequiredSkills());
            missingPreferredSkills = canonicalNames(gap.missingPreferredSkills());
            ExperienceGap experienceGap = gap.experienceGap();
            if (experienceGap != null) {
                knowable = experienceGap.knowable();
                requiredYears = experienceGap.requiredYears();
                candidateYears = experienceGap.candidateYears();
            }
            candidateTrack = gap.candidateTrack() != null ? gap.candidateTrack().name() : "";
            jobTrack = gap.jobTrack() != null ? gap.jobTrack().name() : "";
            trackMismatch = gap.trackMismatch();
            gapSeverity = gap.overallGapSeverity() != null ? gap.overallGapSeverity().name() : "";
        }

        return new AdvisorScoreBreakdown(
                atsScore,
                skillFit,
                roleFit,
                experienceFit,
                educationFit,
                locationFit,
                trackFit,
                matchedRequired,
                missingRequired,
                matchedPreferred,
                missingPreferred,
                projectEvidence,
                experienceEvidence,
                knowable,
                requiredYears,
                candidateYears,
                candidateTrack,
                jobTrack,
                trackMismatch,
                gapSeverity,
                atsExplanation,
                matchedRequiredSkills,
                matchedPreferredSkills,
                missingRequiredSkills,
                missingPreferredSkills);
    }

    /** Canonical skill names in the deterministic order the gap analysis produced. */
    private static List<String> canonicalNames(List<com.agentplatform.orchestrator.gap.SkillGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }
        return gaps.stream()
                .filter(g -> g != null && g.canonicalSkill() != null && !g.canonicalSkill().isBlank())
                .map(com.agentplatform.orchestrator.gap.SkillGap::canonicalSkill)
                .toList();
    }
}
