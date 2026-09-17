package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.advisor.ApplicationRecommendation;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.gap.ImprovementPlanItem;
import com.agentplatform.orchestrator.gap.ImprovementPriority;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import java.time.Instant;
import java.util.List;

/**
 * Immutable evaluation snapshot of a completed orchestration run.
 *
 * <p>Aggregates deterministic metrics already produced by the pipeline stages.
 * All fields are {@code null} when the corresponding stage was skipped, failed,
 * or produced no usable output. No external AI calls, no persistence,
 * no clocks, no randomness — pure observation of existing results.</p>
 *
 * <p>Contains no PII, prompts, model responses, secrets, or raw agent messages.</p>
 */
public record EvaluationResult(
        // Pipeline health (derived from OrchestrationRun)
        boolean pipelineCompleted,
        int agentsCompleted,
        int agentsFailed,
        int agentsSkipped,

        // Resource efficiency (from OrchestrationRun)
        int aiCallsUsed,
        int toolCallsUsed,
        long totalDurationMs,

        // Matching quality (nullable if MATCHING skipped/failed)
        Integer matchScore,
        String matchRecommendation,
        Integer skillFitScore,
        Integer roleFitScore,
        Integer locationFitScore,
        Integer experienceFitScore,
        Integer trackFitScore,
        Integer educationFitScore,

        // Gap severity (nullable if GAP skipped/failed)
        String gapSeverity,
        Boolean trackMismatch,
        Integer missingRequiredSkills,
        Integer missingPreferredSkills,
        Boolean experienceKnowable,

        // Application readiness (nullable if ADVISOR skipped/failed)
        String appRecommendation,
        Integer applicationReadinessScore,
        Integer atsReadinessScore,

        // Improvement plan (nullable if PLAN not generated)
        String improvementPlanOrigin,
        Integer improvementItemsCount
) {
    public EvaluationResult {
        // Null-safe normalization for boxed primitives
        agentsCompleted = Math.max(0, agentsCompleted);
        agentsFailed = Math.max(0, agentsFailed);
        agentsSkipped = Math.max(0, agentsSkipped);
        aiCallsUsed = Math.max(0, aiCallsUsed);
        toolCallsUsed = Math.max(0, toolCallsUsed);
        totalDurationMs = Math.max(-1, totalDurationMs);

        if (matchScore != null) matchScore = Math.max(0, Math.min(100, matchScore));
        if (applicationReadinessScore != null) applicationReadinessScore = Math.max(0, Math.min(100, applicationReadinessScore));
        if (atsReadinessScore != null) atsReadinessScore = Math.max(0, Math.min(100, atsReadinessScore));

        if (skillFitScore != null) skillFitScore = Math.max(0, Math.min(100, skillFitScore));
        if (roleFitScore != null) roleFitScore = Math.max(0, Math.min(100, roleFitScore));
        if (locationFitScore != null) locationFitScore = Math.max(0, Math.min(100, locationFitScore));
        if (experienceFitScore != null) experienceFitScore = Math.max(0, Math.min(100, experienceFitScore));
        if (trackFitScore != null) trackFitScore = Math.max(0, Math.min(100, trackFitScore));
        if (educationFitScore != null) educationFitScore = Math.max(0, Math.min(100, educationFitScore));

        if (missingRequiredSkills != null) missingRequiredSkills = Math.max(0, missingRequiredSkills);
        if (missingPreferredSkills != null) missingPreferredSkills = Math.max(0, missingPreferredSkills);
        if (improvementItemsCount != null) improvementItemsCount = Math.max(0, improvementItemsCount);

        matchRecommendation = trim(matchRecommendation);
        gapSeverity = trim(gapSeverity);
        appRecommendation = trim(appRecommendation);
        improvementPlanOrigin = trim(improvementPlanOrigin);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Creates an {@code EvaluationResult} from the completed orchestration artifacts.
     * All extraction is null-safe — missing stages yield {@code null} metrics.
     * Pipeline health and resource metrics are always derived from the run; optional
     * stage metrics require a non-null {@code AgentContext}.
     */
    public static EvaluationResult from(OrchestrationRun run, AgentContext context) {
        if (run == null) {
            return empty();
        }

        // Pipeline health — reuse OrchestrationRun.success() semantics
        boolean pipelineCompleted = run.success();
        int agentsCompleted = run.completedAgents().size();
        int agentsFailed = run.failedAgents().size();
        int agentsSkipped = run.skippedAgents().size();

        // Resource efficiency
        int aiCallsUsed = run.aiCallsUsed();
        int toolCallsUsed = run.toolCallsUsed();
        long totalDurationMs = run.totalDurationMs();

        // Matching quality (requires context)
        Integer matchScore = null;
        String matchRecommendation = null;
        Integer skillFitScore = null;
        Integer roleFitScore = null;
        Integer locationFitScore = null;
        Integer experienceFitScore = null;
        Integer trackFitScore = null;
        Integer educationFitScore = null;

        // Gap severity
        String gapSeverity = null;
        Boolean trackMismatch = null;
        Integer missingRequiredSkills = null;
        Integer missingPreferredSkills = null;
        Boolean experienceKnowable = null;

        // Application readiness
        String appRecommendation = null;
        Integer applicationReadinessScore = null;
        Integer atsReadinessScore = null;

        // Improvement plan
        String improvementPlanOrigin = null;
        Integer improvementItemsCount = null;

        if (context != null) {
            var jobMatchResult = context.jobMatchResult();
            if (jobMatchResult != null && jobMatchResult.matches() != null && !jobMatchResult.matches().isEmpty()) {
                var firstMatch = jobMatchResult.matches().get(0);
                matchScore = firstMatch.matchScore();
                matchRecommendation = firstMatch.recommendation() != null
                        ? firstMatch.recommendation().name()
                        : null;
                skillFitScore = pct(firstMatch.skillScore());
                roleFitScore = pct(firstMatch.roleScore());
                locationFitScore = pct(firstMatch.locationScore());
                experienceFitScore = pct(firstMatch.experienceScore());
                trackFitScore = pct(firstMatch.trackScore());
                educationFitScore = pct(firstMatch.educationScore());
            }

            // Gap severity
            var gap = context.careerGapAnalysis();
            if (gap != null) {
                gapSeverity = gap.overallGapSeverity() != null
                        ? gap.overallGapSeverity().name()
                        : null;
                trackMismatch = gap.trackMismatch();
                missingRequiredSkills = gap.missingRequiredSkills() != null
                        ? gap.missingRequiredSkills().size()
                        : 0;
                missingPreferredSkills = gap.missingPreferredSkills() != null
                        ? gap.missingPreferredSkills().size()
                        : 0;
                var expGap = gap.experienceGap();
                experienceKnowable = expGap != null ? expGap.knowable() : null;
            }

            // Application readiness
            var advisor = context.applicationAdvisorResponse();
            if (advisor != null) {
                appRecommendation = advisor.recommendation() != null
                        ? advisor.recommendation().name()
                        : null;
                applicationReadinessScore = advisor.applicationReadinessScore();
                // ATS score from the breakdown if available, else 0
                atsReadinessScore = advisor.scoreBreakdown() != null
                        ? advisor.scoreBreakdown().atsReadinessScore()
                        : 0;
            }

            // Improvement plan
            var plan = context.improvementPlan();
            if (plan != null) {
                improvementPlanOrigin = plan.origin();
                improvementItemsCount = plan.priorityItems() != null
                        ? plan.priorityItems().size()
                        : 0;
            }
        }

        return new EvaluationResult(
                pipelineCompleted,
                agentsCompleted, agentsFailed, agentsSkipped,
                aiCallsUsed, toolCallsUsed, totalDurationMs,
                matchScore, matchRecommendation,
                skillFitScore, roleFitScore, locationFitScore,
                experienceFitScore, trackFitScore, educationFitScore,
                gapSeverity, trackMismatch, missingRequiredSkills,
                missingPreferredSkills, experienceKnowable,
                appRecommendation, applicationReadinessScore, atsReadinessScore,
                improvementPlanOrigin, improvementItemsCount
        );
    }

    private static int pct(double factor) {
        if (Double.isNaN(factor)) return 0;
        return Math.max(0, Math.min(100, (int) Math.round(factor * 100.0)));
    }

    private static EvaluationResult empty() {
        return new EvaluationResult(false, 0, 0, 0, 0, 0, -1,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null);
    }
}