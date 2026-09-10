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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ApplicationAdvisorService.class);

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
        log.info("Application advisor START: candidateId={}, jobId={}",
                request.candidateId(), request.jobId());

        CandidateProfile profile = profilePersistenceService.getByIdOrThrow(request.candidateId()).toDomain();
        Job job = jobSearchService.findById(request.jobId())
                .orElseThrow(() -> new JobNotFoundException(request.jobId()));
        // Logging the resolved job source is what makes a live-vs-development data
        // problem visible without exposing any resume content.
        log.info("Application advisor resolved inputs: job='{}', source={}, requiredSkills={}",
                job.title(), job.source(),
                job.requiredSkills() != null ? job.requiredSkills().size() : 0);

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

        // Phase 7.2: deterministic skill-gap driven scoring. Each stage is logged so an
        // unexpected failure in one of them is attributable from the server log.
        CareerGapAnalysis gap = careerGapAnalysisService.analyze(profile, job);
        log.debug("Application advisor stage 1/3 complete: career gap analysis");
        com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis tailoring = resumeTailoringAnalysisService.analyze(profile, job, gap);
        AtsReadinessAnalysis readiness = tailoring.atsReadiness();
        log.debug("Application advisor stage 2/3 complete: ATS readiness");

        // Phase 7.5: multi-factor application recommendation
        // Evaluate the job using the existing JobMatchingService to get the JobMatch score
        // Use the public matchProfileAgainstJobs method with a single job
        JobMatchResult matchResult = jobMatchingService.matchProfileAgainstJobs(profile, List.of(job), null, null, 1);
        JobMatch jobMatch = matchResult.matches().isEmpty() ? null : matchResult.matches().get(0);
        int jobMatchScore = jobMatch != null ? jobMatch.matchScore() : 0;

        // Readiness score from ATS analysis (0–100, deterministic)
        int atsReadinessScore = readiness.score();

        // Phase 7.5: composite score = 60% ATS + 40% Job Match, clamped 0–100
        int compositeScore = (int) Math.round(0.60 * atsReadinessScore + 0.40 * jobMatchScore);
        compositeScore = Math.max(0, Math.min(100, compositeScore));

        // Deterministic recommendation mapping (matches RecommendationLevel bands)
        ApplicationRecommendation recommendation = mapScoreToRecommendation(compositeScore);

        // Strengths: matched required skills with evidence, ATS evidence flags,
        // matched preferred skills, complete-coverage line
        List<String> strengths = buildStrengths(gap, readiness, jobMatch);

        // Concerns: coverage summary, missing skills, experience shortfall/caveat,
        // track mismatch, location/education mismatches, overall severity last
        List<String> concerns = buildConcerns(gap, job, jobMatch);

        // Recommended actions: directly from ImprovementPriority descriptions (preserves rank order)
        List<String> recommendedActions = new ArrayList<>();
        List<RecommendedActionDetail> recommendedActionDetails = new ArrayList<>();
        buildRecommendedActions(gap, recommendedActions, recommendedActionDetails);

        log.info("Application advisor COMPLETE: recommendation={}, composite={}, readiness={}, jobMatch={}",
                recommendation, compositeScore, atsReadinessScore, jobMatchScore);

        // Per-factor transparency: every value is copied from the deterministic results
        // already computed above, so the UI can show the arithmetic behind the score.
        AdvisorScoreBreakdown scoreBreakdown =
                AdvisorScoreBreakdown.from(readiness, jobMatch, gap);
        String recommendationExplanation = buildRecommendationExplanation(
                compositeScore, jobMatchScore, recommendation, scoreBreakdown, gap);

        // Job echo fields: the review UI header shows which role this advice is about.
        // Both values come straight from the already-resolved Job — nothing is invented.
        return ApplicationAdvisorResponse.withBreakdown(
                recommendation,
                compositeScore,
                strengths,
                concerns,
                recommendedActions,
                recommendedActionDetails,
                jobMatchScore,
                job.title(),
                job.company(),
                scoreBreakdown,
                recommendationExplanation);
    }

    /**
     * Builds a plain-language explanation of how the recommendation was reached.
     *
     * <p>Every clause is emitted only when the underlying datum actually exists, so the
     * text never asserts a comparison the platform could not make: an unparseable
     * experience requirement is reported as "not comparable" rather than as a
     * shortfall, and a listing with no stated required skills is reported as such
     * rather than as full coverage. Deterministic — same inputs, same text.</p>
     */
    private static String buildRecommendationExplanation(
            int compositeScore,
            int jobMatchScore,
            ApplicationRecommendation recommendation,
            AdvisorScoreBreakdown b,
            CareerGapAnalysis gap) {

        StringBuilder text = new StringBuilder();
        text.append("Readiness ").append(compositeScore).append("/100 = 60% ATS readiness (")
                .append(b.atsReadinessScore()).append("/100) + 40% job match (")
                .append(jobMatchScore).append("/100). ");

        int totalRequired = b.matchedRequiredCount() + b.missingRequiredCount();
        if (totalRequired > 0) {
            text.append("Required skills: ").append(b.matchedRequiredCount())
                    .append(" of ").append(totalRequired).append(" matched");
            if (b.missingRequiredCount() > 0) {
                text.append(", ").append(b.missingRequiredCount()).append(" missing");
            }
            text.append(". ");
        } else {
            text.append("This listing states no required skills, so skill fit cannot be scored. ");
        }

        text.append(experienceClause(b, gap));

        text.append("Location fit ").append(b.locationFitScore())
                .append("/100, education fit ").append(b.educationFitScore())
                .append("/100, career-track fit ").append(b.trackFitScore()).append("/100. ");

        if (!b.gapSeverity().isEmpty() && !"NO_GAP".equals(b.gapSeverity())) {
            text.append("Overall gap severity: ").append(humanize(b.gapSeverity())).append(". ");
        }

        text.append("That places this application in the ").append(humanize(recommendation.name()))
                .append(" band").append(bandRange(recommendation)).append(".");
        return text.toString();
    }

    /**
     * Experience sentence. Only states a shortfall when both sides carried structured
     * years; otherwise says plainly that the comparison could not be made.
     */
    private static String experienceClause(AdvisorScoreBreakdown b, CareerGapAnalysis gap) {
        Integer required = b.requiredYears();
        Integer candidate = b.candidateYears();

        if (b.experienceKnowable() && required != null && required == 0) {
            return "Experience: the role is entry level, so no minimum years apply. ";
        }
        if (b.experienceKnowable() && required != null && candidate != null) {
            Integer gapYears = gap != null && gap.experienceGap() != null
                    ? gap.experienceGap().gapYears() : null;
            StringBuilder text = new StringBuilder("Experience: ")
                    .append(candidate).append(candidate == 1 ? " year" : " years")
                    .append(" on the resume against ")
                    .append(required).append(required == 1 ? " year" : " years")
                    .append(" required");
            if (gapYears != null && gapYears > 0) {
                text.append(" — ").append(gapYears).append(gapYears == 1 ? " year" : " years").append(" short");
            } else {
                text.append(" — requirement met");
            }
            return text.append(". ").toString();
        }
        if (required != null) {
            return "Experience: the role asks for " + required + " year(s), but no structured years "
                    + "could be read from the resume, so no shortfall is claimed. ";
        }
        return "Experience: not enough structured data on either side to compare years. ";
    }

    /** The readiness range a recommendation band covers, from the scoring thresholds. */
    private static String bandRange(ApplicationRecommendation recommendation) {
        return switch (recommendation) {
            case STRONGLY_RECOMMENDED -> " (readiness 90-100)";
            case RECOMMENDED -> " (readiness 75-89)";
            case APPLY_WITH_IMPROVEMENTS -> " (readiness 60-74)";
            case LOW_PRIORITY -> " (readiness 40-59)";
            case NOT_RECOMMENDED -> " (readiness below 40)";
        };
    }

    /** Turns an enum constant name into sentence-case words ("APPLY_WITH_IMPROVEMENTS" → "Apply with improvements"). */
    private static String humanize(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return "";
        }
        String[] words = enumName.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
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
     * preferred skills, and complete-coverage line.
     *
     * <p>Deterministic order: required skills, ATS evidence lines, preferred skills,
     * complete-coverage line. Required-skill wording and order are unchanged from
     * Phase 7.2/7.3.</p>
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

        // Phase 7.5B: Strong role alignment explanation
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

        // Track mismatch (Phase 7.2 behavior, unchanged)
        if (gap.trackMismatch()) {
            concerns.add("Career track mismatch: candidate track (" + gap.candidateTrack() + ") differs from job track (" + gap.jobTrack() + ")");
        }

        // Phase 7.5B: Location mismatch explanation
        if (jobMatch != null && jobMatch.locationScore() < 0.50) {
            int locPct = (int) Math.round(jobMatch.locationScore() * 100);
            concerns.add("Location mismatch (score: " + locPct + "%)");
        }

        // Phase 7.5B: Education mismatch explanation
        if (jobMatch != null && jobMatch.educationScore() < 0.50) {
            int eduPct = (int) Math.round(jobMatch.educationScore() * 100);
            concerns.add("Education mismatch (score: " + eduPct + "%)");
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

