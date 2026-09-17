package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.advisor.ApplicationRecommendation;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.gap.ImprovementPlanItem;
import com.agentplatform.orchestrator.gap.ImprovementPriority;
import com.agentplatform.orchestrator.gap.SkillGap;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link EvaluationResult}.
 *
 * <p>Hermetic: no Spring, no DB, no LLM. Directly constructs
 * {@link OrchestrationRun}, {@link AgentContext}, and domain results.</p>
 */
@DisplayName("EvaluationResult — deterministic evaluation extraction (Phase 10)")
class EvaluationResultTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private AgentResult completed(AgentType type) {
        return new AgentResult(type, AgentStatus.COMPLETED, true, "done", null,
                "NONE", T0, T0.plusMillis(10));
    }

    private AgentResult skipped(AgentType type, String reason) {
        return new AgentResult(type, AgentStatus.SKIPPED, false, reason, null,
                "NONE", T0, T0.plusMillis(5));
    }

    private AgentResult failed(AgentType type, String code) {
        return new AgentResult(type, AgentStatus.FAILED, false, "boom", null,
                code, T0, T0.plusMillis(5));
    }

    // ─── Full pipeline evaluation ────────────────────────────────────────────

    @Test
    @DisplayName("full run extracts pipeline health, resource metrics, and all stage metrics")
    void fullRunExtractsAllMetrics() {
        var run = new OrchestrationRun(RunStatus.COMPLETED, List.of(
                completed(AgentType.RESUME),
                completed(AgentType.JOB_DISCOVERY),
                completed(AgentType.MATCHING),
                completed(AgentType.CAREER_ADVISOR),
                completed(AgentType.APPLICATION_ADVISOR)
        ), 2, 3, true, "ok", null);

        var context = new AgentContext();
        // Populate matching
        var match = new JobMatch(null, 85, RecommendationLevel.STRONG_MATCH,
                List.of("Java"), List.of(), List.of(), List.of(),
                true, true, ExperienceMatchLevel.STRONG_MATCH,
                com.agentplatform.orchestrator.matching.CareerTrack.SOFTWARE,
                "explanation", List.of(), List.of(),
                0.85, 0.9, 0.8, 0.7, 0.75, 0.6);
        context.setJobMatchResult(new JobMatchResult(1L, "Alice", 1, List.of(match), "mock", true, "ok"));
        // Populate gap
        var gap = new CareerGapAnalysis(1L, "job-1",
                List.of(new SkillGap("Java", List.of(ResumeEvidence.SourceSection.SKILLS))),
                List.of(new SkillGap("Python", List.of())),
                List.of(), List.of(),
                new ExperienceGap(5, 3, 2, true),
                com.agentplatform.orchestrator.matching.CareerTrack.SOFTWARE,
                com.agentplatform.orchestrator.matching.CareerTrack.SOFTWARE,
                false, GapSeverity.LOW,
                List.of(ImprovementPriority.requiredSkill(1, "Python")));
        context.setCareerGapAnalysis(gap);
        // Populate advisor
        var advisor = com.agentplatform.orchestrator.advisor.ApplicationAdvisorResponse.of(
                ApplicationRecommendation.RECOMMENDED, 80,
                List.of(), List.of(), List.of(), List.of(),
                85, "Engineer", "Acme");
        context.setApplicationAdvisorResponse(advisor);
        // Populate plan
        context.setImprovementPlan(new com.agentplatform.orchestrator.gap.CareerImprovementPlan(
                "job-1", 1L, "summary", "DETERMINISTIC",
                List.of(new com.agentplatform.orchestrator.gap.ImprovementPlanItem(
                        1, "Java", "REQUIRED_SKILL", "required by target job",
                        "Java is required by the target job but is not present in the candidate profile.",
                        List.of()))));

        var eval = EvaluationResult.from(run, context);

        // Pipeline health
        assertTrue(eval.pipelineCompleted());
        assertEquals(5, eval.agentsCompleted());
        assertEquals(0, eval.agentsFailed());
        assertEquals(0, eval.agentsSkipped());
        // Resources
        assertEquals(2, eval.aiCallsUsed());
        assertEquals(3, eval.toolCallsUsed());
        assertTrue(eval.totalDurationMs() >= 0);
        // Matching
        assertEquals(85, eval.matchScore());
        assertEquals("STRONG_MATCH", eval.matchRecommendation());
        assertEquals(85, eval.skillFitScore());
        assertEquals(90, eval.roleFitScore());
        assertEquals(80, eval.locationFitScore());
        assertEquals(70, eval.experienceFitScore());
        assertEquals(75, eval.trackFitScore());
        assertEquals(60, eval.educationFitScore());
        // Gap
        assertEquals("LOW", eval.gapSeverity());
        assertEquals(false, eval.trackMismatch());
        assertEquals(1, eval.missingRequiredSkills());
        assertEquals(0, eval.missingPreferredSkills());
        assertEquals(true, eval.experienceKnowable());
        // Advisor
        assertEquals("RECOMMENDED", eval.appRecommendation());
        assertEquals(80, eval.applicationReadinessScore());
        assertEquals(0, eval.atsReadinessScore()); // no breakdown
        // Plan
        assertEquals("DETERMINISTIC", eval.improvementPlanOrigin());
        assertEquals(1, eval.improvementItemsCount());
    }

    // ─── Partial/skipped stages ──────────────────────────────────────────────

    @Test
    @DisplayName("skipped MATCHING/GAP/ADVISOR yield null metrics — no fabricated values")
    void skippedStagesProduceNullMetrics() {
        var run = new OrchestrationRun(RunStatus.PARTIAL, List.of(
                completed(AgentType.RESUME),
                completed(AgentType.JOB_DISCOVERY),
                skipped(AgentType.MATCHING, "no job"),
                skipped(AgentType.CAREER_ADVISOR, "no job"),
                skipped(AgentType.APPLICATION_ADVISOR, "no job")
        ), 0, 0, false, "partial", null);

        var context = new AgentContext(); // empty — no domain results

        var eval = EvaluationResult.from(run, context);

        assertTrue(eval.pipelineCompleted() == false);
        assertEquals(2, eval.agentsCompleted());
        assertEquals(0, eval.agentsFailed());
        assertEquals(3, eval.agentsSkipped());
        // Matching absent
        assertNull(eval.matchScore());
        assertNull(eval.matchRecommendation());
        assertNull(eval.skillFitScore());
        // Gap absent
        assertNull(eval.gapSeverity());
        assertNull(eval.trackMismatch());
        assertNull(eval.experienceKnowable());
        // Advisor absent
        assertNull(eval.appRecommendation());
        assertNull(eval.applicationReadinessScore());
        // Plan absent
        assertNull(eval.improvementPlanOrigin());
        assertNull(eval.improvementItemsCount());
    }

    @Test
    @DisplayName("failed blocking agent sets pipelineCompleted=false")
    void blockingFailureSetsPipelineCompletedFalse() {
        var run = new OrchestrationRun(RunStatus.FAILED, List.of(
                failed(AgentType.RESUME, "AGENT_INPUT_MISSING"),
                skipped(AgentType.JOB_DISCOVERY, "skip"),
                skipped(AgentType.MATCHING, "skip"),
                skipped(AgentType.CAREER_ADVISOR, "skip"),
                skipped(AgentType.APPLICATION_ADVISOR, "skip")
        ), 0, 0, false, "failed", AgentType.RESUME);

        var eval = EvaluationResult.from(run, new AgentContext());

        assertTrue(eval.pipelineCompleted() == false);
        assertEquals(0, eval.agentsCompleted());
        assertEquals(1, eval.agentsFailed());
        assertEquals(4, eval.agentsSkipped());
    }

    // ─── Null safety ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null run returns empty; null context returns run-derived metrics")
    void nullInputsReturnEmpty() {
        var empty1 = EvaluationResult.from(null, new AgentContext());
        var runWithNullContext = new OrchestrationRun(RunStatus.COMPLETED, List.of(), 0, 0, true, "ok", null);
        var empty2 = EvaluationResult.from(runWithNullContext, null);
        var empty3 = EvaluationResult.from(null, null);

        assertTrue(empty1.pipelineCompleted() == false);
        assertEquals(0, empty1.agentsCompleted());
        assertEquals(-1, empty1.totalDurationMs()); // default
        // null context with valid run returns run-derived metrics
        assertTrue(empty2.pipelineCompleted());
        assertEquals(0, empty2.agentsCompleted());
        assertTrue(empty3.pipelineCompleted() == false);
    }

    // ─── Clamping/bounds ──────────────────────────────────────────────────────

    @Test
    @DisplayName("scores clamped to 0-100")
    void scoresClamped() {
        var run = new OrchestrationRun(RunStatus.COMPLETED, List.of(
                completed(AgentType.RESUME),
                completed(AgentType.JOB_DISCOVERY),
                completed(AgentType.MATCHING),
                completed(AgentType.CAREER_ADVISOR),
                completed(AgentType.APPLICATION_ADVISOR)
        ), 0, 0, true, "ok", null);

        var context = new AgentContext();
        // JobMatch with out-of-range factor scores
        var match = new JobMatch(null, 150, RecommendationLevel.EXCELLENT_MATCH,
                List.of(), List.of(), List.of(), List.of(),
                true, true, ExperienceMatchLevel.STRONG_MATCH,
                com.agentplatform.orchestrator.matching.CareerTrack.SOFTWARE,
                "explanation", List.of(), List.of(),
                1.5, -0.2, 0.5, 0.5, 0.5, 0.5); // 150% and -20% will be clamped
        context.setJobMatchResult(new JobMatchResult(1L, "Alice", 1, List.of(match), "mock", true, "ok"));

        var eval = EvaluationResult.from(run, context);

        assertEquals(100, eval.matchScore()); // clamped to 100
        assertEquals(100, eval.skillFitScore()); // 1.5 -> 150 -> clamped to 100
        assertEquals(0, eval.roleFitScore()); // -0.2 -> -20 -> clamped to 0
    }

    // ─── Determinism ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("identical inputs produce identical evaluation")
    void deterministic() {
        var run = new OrchestrationRun(RunStatus.COMPLETED, List.of(
                completed(AgentType.RESUME),
                completed(AgentType.JOB_DISCOVERY),
                completed(AgentType.MATCHING),
                completed(AgentType.CAREER_ADVISOR),
                completed(AgentType.APPLICATION_ADVISOR)
        ), 1, 2, true, "ok", null);

        var eval1 = EvaluationResult.from(run, new AgentContext());
        var eval2 = EvaluationResult.from(run, new AgentContext());

        assertEquals(eval1, eval2);
    }
}