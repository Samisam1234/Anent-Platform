package com.agentplatform.orchestrator.advisor;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Advisor service with deterministic skill-gap driven readiness scoring.
 *
 * <p>Phase 7.2 computes the readiness score directly from {@link AtsReadinessAnalysis}
 * and maps it to a controlled recommendation. Phase 7.3 enriches the explanatory
 * strengths (matched preferred skills, complete-coverage line) and concerns
 * (coverage summary, unknown-experience caveat) without changing scoring,
 * recommendation thresholds, or recommended actions. All data comes from existing
 * deterministic services — no LLM, no external calls, no fabricated fields.</p>
 *
 * <p>Safety guarantees (preserved from Phase 1–6):</p>
 * <ul>
 *   <li>Advice only — never submits an application</li>
 *   <li>Never approves an application</li>
 *   <li>Never sends an email</li>
 *   <li>Never invokes {@code EmailTools}</li>
 *   <li>Never silently modifies {@code JobApplication} state</li>
 *   <li>Never bypasses the existing user-controlled application/email workflow</li>
 * </ul>
 */
@Service
public class ApplicationAdvisorService {

    private final CandidateProfilePersistenceService profilePersistenceService;
    private final JobSearchService jobSearchService;
    private final CareerGapAnalysisService careerGapAnalysisService;
    private final ResumeTailoringAnalysisService resumeTailoringAnalysisService;
    private final JobMatchingService jobMatchingService;

    public ApplicationAdvisorService(
            CandidateProfilePersistenceService profilePersistenceService,
            JobSearchService jobSearchService,
            CareerGapAnalysisService careerGapAnalysisService,
            ResumeTailoringAnalysisService resumeTailoringAnalysisService,
            JobMatchingService jobMatchingService) {
        this.profilePersistenceService = profilePersistenceService;
        this.jobSearchService = jobSearchService;
        this.careerGapAnalysisService = careerGapAnalysisService;
        this.resumeTailoringAnalysisService = resumeTailoringAnalysisService;
        this.jobMatchingService = jobMatchingService;
    }

    /**
     * Advises on a job application for the given candidate and job.
     *
     * @param request the advisor request carrying only safe identifiers
     * @return a structured {@link ApplicationAdvisorResponse}
     * @throws CandidateProfileNotFoundException if the candidate does not exist
     * @throws JobNotFoundException if the job does not exist
     */
    public ApplicationAdvisorResponse advise(ApplicationAdvisorRequest request) {
        ApplicationAdvisorRequest.validate(request);

        CandidateProfile profile = profilePersistenceService.getByIdOrThrow(request.candidateId()).toDomain();
        Job job = jobSearchService.findById(request.jobId())
                .orElseThrow(() -> new JobNotFoundException(request.jobId()));

        return adviseFromDomain(request.candidateId(), profile, job);
    }

    /**
     * Advises on a job application from already-resolved domain models.
     *
     * @param candidateId the candidate identifier
     * @param profile the resolved candidate profile
     * @param job the resolved job
     * @return a structured {@link ApplicationAdvisorResponse}
     */
    public ApplicationAdvisorResponse adviseFromDomain(
            Long candidateId,
            CandidateProfile profile,
            Job job) {

        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("A positive candidateId is required.");
        }
        if (profile == null) {
            throw new IllegalArgumentException("Candidate profile must not be null.");
        }
        if (job == null) {
            throw new IllegalArgumentException("Job must not be null.");
        }

        // Phase 7.2: deterministic skill-gap driven scoring
        CareerGapAnalysis gap = careerGapAnalysisService.analyze(profile, job);
        com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis tailoring = resumeTailoringAnalysisService.analyze(profile, job, gap);
        AtsReadinessAnalysis readiness = tailoring.atsReadiness();

        // Phase 7.5: multi-factor application recommendation
        // Evaluate the job using the existing JobMatchingService to get the JobMatch score
        // Use the public matchProfileAgainstJobs method with a single job
        JobMatchResult matchResult = jobMatchingService.matchProfileAgainstJobs(profile, List.of(job), null, null, 1);
        JobMatch jobMatch = matchResult.matches().isEmpty() ? null : matchResult.matches().get(0);
        int jobMatchScore = jobMatch != null ? jobMatch.matchScore() : 0;

        // Readiness score from ATS analysis (0–100, deterministic)
        int atsReadinessScore = readiness.score();

        // Phase 7.5: composite score = 60% ATS + 40% Job Match, clamped 0–100
        int compositeScore = (int) Math.round(0.60 * atsReadinessScore + 0.40 * jobMatch.matchScore());
        compositeScore = Math.max(0, Math.min(100, compositeScore));

        // Deterministic recommendation mapping (matches RecommendationLevel bands)
        ApplicationRecommendation recommendation = mapScoreToRecommendation(compositeScore);

        // Strengths: matched required skills with evidence, ATS evidence flags,
        // matched preferred skills, complete-coverage line, and JobMatch role alignment
        List<String> strengths = buildStrengths(gap, readiness, jobMatch);

        // Concerns: coverage summary, missing skills, experience shortfall/caveat,
        // track mismatch, location/education mismatches, overall severity last
        List<String> concerns = buildConcerns(gap, job, jobMatch);

        // Recommended actions: directly from ImprovementPriority descriptions (preserves rank order)
        List<String> recommendedActions = new ArrayList<>();
        List<RecommendedActionDetail> recommendedActionDetails = new ArrayList<>();
        buildRecommendedActions(gap, recommendedActions, recommendedActionDetails);

        return new ApplicationAdvisorResponse(
                recommendation,
                compositeScore,
                strengths,
                concerns,
                recommendedActions,
                recommendedActionDetails,
                jobMatchScore);
    }

    /**
     * Maps a 0–100 score to the controlled recommendation enum.
     * Thresholds mirror {@link RecommendationLevel} bands.
     */
    private static ApplicationRecommendation mapScoreToRecommendation(int score) {
        if (score >= 90) {
            return ApplicationRecommendation.STRONGLY_RECOMMENDED;
        }
        if (score >= 75) {
            return ApplicationRecommendation.RECOMMENDED;
        }
        if (score >= 60) {
            return ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS;
        }
        if (score >= 40) {
            return ApplicationRecommendation.LOW_PRIORITY;
        }
        return ApplicationRecommendation.NOT_RECOMMENDED;
    }

    /**
     * Builds strengths from matched required skills, ATS evidence flags, matched
     * preferred skills, complete-coverage line, and JobMatch role alignment.
     *
     * <p>Deterministic order: required skills, ATS evidence lines, preferred skills,
     * complete-coverage line, JobMatch role alignment. Required-skill wording and order
     * are unchanged from Phase 7.2/7.3.</p>
     */
    private static List<String> buildStrengths(CareerGapAnalysis gap, AtsReadinessAnalysis readiness, JobMatch jobMatch) {
        List<String> strengths = new ArrayList<>();

        // Matched required skills with evidence sources (Phase 7.2 behavior, unchanged)
        if (gap.matchedRequiredSkills() != null) {
            for (var skillGap : gap.matchedRequiredSkills()) {
                String skill = skillGap.canonicalSkill();
                if (skill != null && !skill.isBlank()) {
                    var sources = skillGap.evidenceSources();
                    if (sources != null && !sources.isEmpty()) {
                        strengths.add(skill + " (evidence: " + formatSources(sources) + ")");
                    } else {
                        strengths.add(skill);
                    }
                }
            }
        }

        // ATS project evidence (Phase 7.2 behavior, unchanged)
        if (readiness.relevantProjectEvidence()) {
            strengths.add("Relevant project evidence present");
        }

        // ATS experience evidence (Phase 7.2 behavior, unchanged)
        if (readiness.relevantExperienceEvidence()) {
            strengths.add("Relevant experience evidence present");
        }

        // Phase 7.3: matched preferred skills, explicitly labeled, after required/ATS lines
        if (gap.matchedPreferredSkills() != null) {
            for (var skillGap : gap.matchedPreferredSkills()) {
                String skill = skillGap.canonicalSkill();
                if (skill != null && !skill.isBlank()) {
                    var sources = skillGap.evidenceSources();
                    if (sources != null && !sources.isEmpty()) {
                        strengths.add("Preferred match: " + skill + " (evidence: " + formatSources(sources) + ")");
                    } else {
                        strengths.add("Preferred match: " + skill);
                    }
                }
            }
        }

        // Phase 7.3: complete-coverage line only when zero required skills are missing
        int matchedRequired = gap.matchedRequiredSkills() != null ? gap.matchedRequiredSkills().size() : 0;
        int missingRequired = gap.missingRequiredSkills() != null ? gap.missingRequiredSkills().size() : 0;
        if (missingRequired == 0) {
            int totalRequired = matchedRequired + missingRequired;
            strengths.add("Complete required-skill coverage (" + totalRequired + "/" + totalRequired + ")");
        }

        // Phase 7.5: JobMatch role alignment strength
        if (jobMatch != null && jobMatch.roleScore() >= 0.80) {
            int rolePct = (int) Math.round(jobMatch.roleScore() * 100);
            strengths.add("Strong role alignment (score: " + rolePct + "%)");
        }

        return List.copyOf(strengths);
    }

    /**
     * Builds concerns from the coverage summary, missing skills, experience
     * shortfall (or unknown-experience caveat), track mismatch, location/education
     * mismatches from JobMatch, and overall severity.
     *
     * <p>Deterministic order: coverage summary, missing required lines, missing
     * preferred lines, experience line, location mismatch, education mismatch,
     * track mismatch, overall severity (final). Per-skill, shortfall, mismatch
     * and severity wording are unchanged from Phase 7.2/7.3.</p>
     */
    private static List<String> buildConcerns(CareerGapAnalysis gap, Job job, JobMatch jobMatch) {
        List<String> concerns = new ArrayList<>();

        int matchedRequired = gap.matchedRequiredSkills() != null ? gap.matchedRequiredSkills().size() : 0;
        int missingRequired = gap.missingRequiredSkills() != null ? gap.missingRequiredSkills().size() : 0;
        int totalRequired = matchedRequired + missingRequired;

        // Phase 7.3: required-skill coverage summary (first concern line)
        concerns.add("Missing " + missingRequired + " of " + totalRequired + " required skills");

        // Missing required skills (highest concern; Phase 7.2 behavior, unchanged)
        if (gap.missingRequiredSkills() != null) {
            for (var skillGap : gap.missingRequiredSkills()) {
                String skill = skillGap.canonicalSkill();
                if (skill != null && !skill.isBlank()) {
                    concerns.add("Missing required skill: " + skill);
                }
            }
        }

        // Missing preferred skills (Phase 7.2 behavior, unchanged)
        if (gap.missingPreferredSkills() != null) {
            for (var skillGap : gap.missingPreferredSkills()) {
                String skill = skillGap.canonicalSkill();
                if (skill != null && !skill.isBlank()) {
                    concerns.add("Missing preferred skill: " + skill);
                }
            }
        }

        // Experience shortfall (Phase 7.2 behavior, unchanged)
        if (gap.experienceGap() != null && gap.experienceGap().hasShortfall()) {
            int gapYears = gap.experienceGap().gapYears() != null ? gap.experienceGap().gapYears() : 0;
            concerns.add("Experience shortfall: " + gapYears + " year" + (gapYears == 1 ? "" : "s") + " below requirement");
        } else if (job != null && job.experienceRequirement() != null && !job.experienceRequirement().isBlank()
                && gap.experienceGap() != null && !gap.experienceGap().knowable()) {
            // Phase 7.3: explicit caveat when a requirement exists but years are not determinable
            concerns.add("Experience requirement present but candidate years not determinable from structured data");
        }

        // Phase 7.5: JobMatch location mismatch concern
        if (jobMatch != null && jobMatch.locationScore() < 0.50) {
            int locPct = (int) Math.round(jobMatch.locationScore() * 100);
            concerns.add("Location mismatch (score: " + locPct + "%)");
        }

        // Phase 7.5: JobMatch education mismatch concern
        if (jobMatch != null && jobMatch.educationScore() < 0.50) {
            int eduPct = (int) Math.round(jobMatch.educationScore() * 100);
            concerns.add("Education mismatch (score: " + eduPct + "%)");
        }

        // Track mismatch (Phase 7.2 behavior, unchanged)
        if (gap.trackMismatch()) {
            concerns.add("Career track mismatch: candidate track (" + gap.candidateTrack() + ") differs from job track (" + gap.jobTrack() + ")");
        }

        // Overall gap severity, exactly once and always final (Phase 7.2 behavior, unchanged)
        if (gap.overallGapSeverity() != null && gap.overallGapSeverity() != com.agentplatform.orchestrator.gap.GapSeverity.NO_GAP) {
            concerns.add("Overall gap severity: " + gap.overallGapSeverity());
        }

        return List.copyOf(concerns);
    }

    /**
     * Builds recommended actions and structured action details from ImprovementPriority descriptions.
     * Preserves the existing ranked order: required skills → experience → preferred skills.
     * Both recommendedActions and recommendedActionDetails are derived from the same
     * ordered ImprovementPriority list and stay in identical rank order.
     */
    private static void buildRecommendedActions(
            CareerGapAnalysis gap,
            List<String> actions,
            List<RecommendedActionDetail> actionDetails) {

        if (gap.improvementPriorities() != null) {
            for (var priority : gap.improvementPriorities()) {
                String desc = priority.description();
                if (desc != null && !desc.isBlank()) {
                    actions.add(desc);
                    actionDetails.add(RecommendedActionDetail.fromImprovementPriority(priority));
                }
            }
        }
    }

    private static String formatSources(List<com.agentplatform.orchestrator.resume.ResumeEvidence.SourceSection> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        return sources.stream()
                .map(Enum::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}

