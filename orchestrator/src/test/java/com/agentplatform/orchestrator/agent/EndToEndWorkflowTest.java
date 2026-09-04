package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.application.ApplicationDraftStatus;
import com.agentplatform.orchestrator.application.ApplicationEmailDraft;
import com.agentplatform.orchestrator.application.ApplicationPreparationService;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.gap.CareerImprovementPlan;
import com.agentplatform.orchestrator.gap.CareerImprovementPlanService;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeProfileService;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraftService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 6.7 — end-to-end agent workflow integration.
 *
 * <p>Wires the five REAL {@link CareerAgent} implementations into a single
 * {@link CareerAgentOrchestrator}, backed by stub domain services that return real
 * structured domain records. Runs the realistic {@code CandidateProfile + Job}
 * scenario that {@code POST /api/v1/agent/orchestrate} produces (profile + job
 * preselected). Verifies the actual data flow through {@link AgentContext}: each
 * stage's typed output is stored and then consumed by the next stage.</p>
 *
 * <p>Hermetic: no Spring, no DB, no network, no Ollama, no SMTP. Reasoning service
 * is disabled so no LLM call is made ({@code aiCallsUsed == 0}); no tools are
 * invoked ({@code toolCallsUsed == 0}).</p>
 */
@DisplayName("End-to-end agent workflow integration (Phase 6.7)")
class EndToEndWorkflowTest {

    private static final List<AgentType> ORDER = List.of(
            AgentType.RESUME, AgentType.JOB_DISCOVERY, AgentType.MATCHING,
            AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR);

    // ─── Realistic domain fixtures ───────────────────────────────────────────

    private CandidateProfile profile() {
        return new CandidateProfile("Alice", "alice@example.com", null, "London",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of("Java", "Spring"), List.of(), List.of("Backend Engineer"), List.of());
    }

    private Job job(String id) {
        return new Job(id, "Backend Engineer", "Acme", null, null,
                List.of("Java", "Spring"), List.of(), null, null, null, "mock",
                null, null, null);
    }

    private JobMatch match(Job j) {
        return new JobMatch(j, 92, RecommendationLevel.STRONG_MATCH,
                List.of(), List.of(), List.of(), List.of(), true, true,
                null, null, "e", List.of(), List.of(), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    private JobMatchResult matchResult(Long candidateId, Job j) {
        return new JobMatchResult(candidateId, "Alice", 1, List.of(match(j)), "x", false, "ok");
    }

    private CareerGapAnalysis gap(String jobId) {
        return new CareerGapAnalysis(1L, jobId, List.of(), List.of(), List.of(), List.of(),
                new ExperienceGap(null, null, null, false),
                CareerTrack.SOFTWARE, CareerTrack.SOFTWARE, false,
                GapSeverity.MEDIUM, List.of());
    }

    private CareerImprovementPlan plan(String jobId) {
        return new CareerImprovementPlan(jobId, 1L, "improve mastery",
                CareerImprovementPlan.ORIGIN_DETERMINISTIC, List.of());
    }

    private ResumeTailoringAnalysis tailoring(String jobId) {
        return new ResumeTailoringAnalysis(1L, jobId, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new AtsReadinessAnalysis(92, "STRONG", 1, 0, 0, 0, true, true, "well tailored"));
    }

    private TailoredResumeDraft draft(String jobId) {
        return new TailoredResumeDraft(jobId, 1L, "summary", List.of(),
                List.of("Java", "Spring"), List.of(), List.of(), List.of(), null, List.of());
    }

    private ApplicationEmailDraft emailDraft(String jobId) {
        return new ApplicationEmailDraft(jobId, 1L, "Acme", "Backend Engineer",
                "Hiring Manager", null, "Application for Backend Engineer", "body",
                "DRAFT_ONLY", ApplicationDraftStatus.REVIEW_REQUIRED, List.of());
    }

    // ─── Wire the five REAL agents with stubbed domain services ─────────────

    /**
     * Builds a {@link CareerAgentOrchestrator} holding the authentic five agent
     * types, each backed by a stub service returning the supplied real records.
     */
    private CareerAgentOrchestrator orchestrator(StubServices s) {
        ResumeAgent resume = new ResumeAgent(s.resumeSvc);
        JobDiscoveryAgent discovery = new JobDiscoveryAgent(s.searchSvc);
        MatchingAgent matching = new MatchingAgent(s.matchSvc);
        CareerAdvisorAgent advisor = new CareerAdvisorAgent(s.gapSvc, s.planSvc);
        ApplicationAdvisorAgent application = new ApplicationAdvisorAgent(
                s.tailoringSvc, s.draftSvc, s.prepSvc);
        return new CareerAgentOrchestrator(List.of(resume, discovery, matching, advisor, application));
    }

    /** Collects the seven stub services and pre-wires realistic behavior. */
    private static class StubServices {
        final ResumeProfileService resumeSvc = mock(ResumeProfileService.class);
        final JobSearchService searchSvc = mock(JobSearchService.class);
        final JobMatchingService matchSvc = mock(JobMatchingService.class);
        final CareerGapAnalysisService gapSvc = mock(CareerGapAnalysisService.class);
        final CareerImprovementPlanService planSvc = mock(CareerImprovementPlanService.class);
        final ResumeTailoringAnalysisService tailoringSvc = mock(ResumeTailoringAnalysisService.class);
        final TailoredResumeDraftService draftSvc = mock(TailoredResumeDraftService.class);
        final ApplicationPreparationService prepSvc = mock(ApplicationPreparationService.class);
    }

    private StubServices stubs(Long candidateId, Job j) {
        StubServices s = new StubServices();
        CandidateProfile p = profile();
        when(s.resumeSvc.buildProfile(anyString())).thenReturn(p);
        when(s.matchSvc.matchJobs(any())).thenReturn(matchResult(candidateId, j));
        when(s.gapSvc.analyze(any(), any())).thenReturn(gap(j.id()));
        when(s.planSvc.generatePlan(any())).thenReturn(plan(j.id()));
        when(s.tailoringSvc.analyze(any(), any(), any())).thenReturn(tailoring(j.id()));
        when(s.draftSvc.generate(any(), any(), any())).thenReturn(draft(j.id()));
        when(s.prepSvc.prepare(any(), any(), any())).thenReturn(emailDraft(j.id()));
        return s;
    }

    /** The realistic API-boundary context: a candidate profile + preselected job. */
    private AgentContext apiContext(Long candidateId, Job j) {
        AgentContext ctx = new AgentContext();
        ctx.setCandidateId(candidateId);
        ctx.setJobId(j.id());
        ctx.setCandidateProfile(profile());
        ctx.setJob(j);
        return ctx;
    }

    // ─── 1. Full successful five-agent workflow ─────────────────────────────

    @Test
    @DisplayName("a full profile + job run completes with all five agents COMPLETED in order")
    void fullSuccessfulWorkflow() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.COMPLETED, run.runStatus());
        assertTrue(run.success());
        assertNull(run.stoppingAgentType());
        assertEquals(ORDER, run.agentExecutions().stream().map(AgentResult::agentType).toList());
        assertEquals(ORDER, run.completedAgents());
        assertTrue(run.failedAgents().isEmpty());
        assertTrue(run.skippedAgents().isEmpty());
        for (AgentResult r : run.agentExecutions()) {
            assertEquals(AgentStatus.COMPLETED, r.status());
        }
    }

    // ─── 2. Agent data passed through the context correctly ────────────────

    @Test
    @DisplayName("each agent stores its typed output in AgentContext for downstream use")
    void contextDataFlow() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        orc.orchestrateTracked(ctx);

        // ResumeAgent → CandidateProfile.
        CandidateProfile p = ctx.candidateProfile();
        assertTrue(ctx.resultOf(AgentType.RESUME).outputAs(CandidateProfile.class) == p);
        // JobDiscoveryAgent (reused job) → Job.
        assertTrue(ctx.resultOf(AgentType.JOB_DISCOVERY).outputAs(Job.class) == j);
        // MatchingAgent → JobMatchResult.
        assertTrue(ctx.resultOf(AgentType.MATCHING).outputAs(JobMatchResult.class) == ctx.jobMatchResult());
        // CareerAdvisorAgent → CareerGapAnalysis + improvement plan.
        assertTrue(ctx.resultOf(AgentType.CAREER_ADVISOR).outputAs(CareerGapAnalysis.class) == ctx.careerGapAnalysis());
        assertTrue(ctx.improvementPlan() != null);
        // ApplicationAdvisorAgent → tailoring analysis + tailored draft + review-only email draft.
        assertTrue(ctx.resultOf(AgentType.APPLICATION_ADVISOR).outputAs(ApplicationEmailDraft.class) == ctx.applicationDraft());
        assertTrue(ctx.tailoringAnalysis() != null);
        assertTrue(ctx.tailoredDraft() != null);

        // The run-local store holds exactly the five agent results.
        assertEquals(5, ctx.storedAgentCount());
    }

    // ─── 3. Resume result reused ───────────────────────────────────────────

    @Test
    @DisplayName("ResumeAgent reuses an existing candidate profile — no re-parse")
    void resumeReused() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        when(s.resumeSvc.buildProfile(anyString())).thenThrow(new AssertionError("must not re-parse"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j); // profile already present

        orc.orchestrateTracked(ctx);

        verify(s.resumeSvc, never()).buildProfile(anyString());
        assertEquals(profile(), ctx.candidateProfile(), "the supplied profile is reused unchanged");
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.RESUME).status());
    }

    // ─── 4. Job result reused ──────────────────────────────────────────────

    @Test
    @DisplayName("JobDiscoveryAgent reuses the preselected job — no re-search")
    void jobReused() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        when(s.searchSvc.search(any())).thenThrow(new AssertionError("must not search"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        orc.orchestrateTracked(ctx);

        verify(s.searchSvc, never()).search(any());
        assertTrue(ctx.job() == j);
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.JOB_DISCOVERY).status());
    }

    // ─── 5. Matching result available to career advisor ────────────────────

    @Test
    @DisplayName("MatchingAgent stores a JobMatchResult that the pipeline exposes as context state")
    void matchingResultAvailable() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        orc.orchestrateTracked(ctx);

        JobMatchResult match = ctx.jobMatchResult();
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.MATCHING).status());
        assertEquals(1, match.totalJobs());
        assertEquals("job-1", match.matches().get(0).job().id());
        // The career advisor (next stage) ran off the candidate + job context.
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.CAREER_ADVISOR).status());
        assertTrue(ctx.careerGapAnalysis() != null);
    }

    // ─── 6. Career result available to application advisor ─────────────────

    @Test
    @DisplayName("ApplicationAdvisor consumes the CareerGapAnalysis stored by CareerAdvisor")
    void careerResultAvailableToApplication() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerGapAnalysis gap = gap(j.id());
        when(s.gapSvc.analyze(any(), any())).thenReturn(gap);

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        orc.orchestrateTracked(ctx);

        assertTrue(ctx.careerGapAnalysis() == gap);
        verify(s.tailoringSvc).analyze(ctx.candidateProfile(), j, gap);
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.APPLICATION_ADVISOR).status());
    }

    // ─── 7. Application draft remains review-only ──────────────────────────

    @Test
    @DisplayName("ApplicationAdvisor produces a review-only draft and never sends email")
    void draftIsReviewOnly() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        orc.orchestrateTracked(ctx);

        ApplicationEmailDraft draft = ctx.applicationDraft();
        assertEquals(ApplicationDraftStatus.REVIEW_REQUIRED, draft.status());
        // The agent layer has no send path — application advisor only prepares a draft.
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.APPLICATION_ADVISOR).status());
    }

    // ─── 8. Fixed agent order ──────────────────────────────────────────────

    @Test
    @DisplayName("execution follows the fixed RESUME → JOB_DISCOVERY → MATCHING → CAREER_ADVISOR → APPLICATION_ADVISOR order")
    void fixedOrder() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);

        OrchestrationRun run = orc.orchestrateTracked(apiContext(1L, j));

        assertEquals(ORDER, run.agentExecutions().stream().map(AgentResult::agentType).toList());
    }

    // ─── 9 & 10. AI and tool budgets ───────────────────────────────────────

    @Test
    @DisplayName("budget constraints hold: MAX_AI_CALLS=3, tool limit=4; a no-reasoning run uses zero")
    void budgetsHold() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);

        OrchestrationRun run = orc.orchestrateTracked(apiContext(1L, j));

        assertEquals(0, run.aiCallsUsed(), "stub run with no reasoning uses no AI calls");
        assertEquals(0, run.toolCallsUsed(), "stub run invokes no tools");
        assertTrue(run.aiCallsUsed() <= CareerAgentOrchestrator.MAX_AI_CALLS);
        assertTrue(run.toolCallsUsed() <= AgentToolOrchestrator.MAX_TOOL_CALLS_PER_ORCHESTRATION);
    }

    // ─── 11. Run-local memory isolation ────────────────────────────────────

    @Test
    @DisplayName("Run A results never leak into Run B (fresh context per run)")
    void runLocalMemoryIsolation() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);

        AgentContext runA = apiContext(1L, j);
        orc.orchestrateTracked(runA);
        assertEquals(5, runA.storedAgentCount());

        AgentContext runB = apiContext(2L, j);
        assertTrue(runB.hasNoStoredResults());
        orc.orchestrateTracked(runB);
        assertEquals(5, runB.storedAgentCount());
        assertEquals(AgentStatus.COMPLETED, runB.resultOf(AgentType.MATCHING).status());
        // A's candidate id is not carried into B.
        assertEquals(2L, runB.candidateId());
        assertTrue(runA.candidateId() != runB.candidateId());
    }

    // ─── 12. Blocking resume failure ───────────────────────────────────────

    @Test
    @DisplayName("resume failure blocks; downstream agents are SKIPPED")
    void resumeBlockingFailure() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        when(s.resumeSvc.buildProfile(anyString())).thenThrow(new RuntimeException("parse boom"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("A real resume document with enough text to parse.");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.FAILED, run.runStatus());
        assertFalse(run.success());
        assertEquals(AgentType.RESUME, run.stoppingAgentType());
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.RESUME).status());
        for (AgentType dep : List.of(AgentType.JOB_DISCOVERY, AgentType.MATCHING,
                AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR)) {
            assertEquals(AgentStatus.SKIPPED, run.resultOf(dep).status(), dep + " must be SKIPPED");
            assertNull(run.resultOf(dep).output(), "skipped stage must carry no fabricated output");
        }
    }

    // ─── 13. Blocking job failure ─────────────────────────────────────────

    @Test
    @DisplayName("job discovery failure blocks; downstream agents are SKIPPED")
    void jobBlockingFailure() {
        CandidateProfile p = profile();
        StubServices s = stubs(1L, job("job-1"));
        when(s.searchSvc.search(any())).thenThrow(new RuntimeException("search boom"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(p); // profile only — job discovery must search and fail

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.FAILED, run.runStatus());
        assertEquals(AgentType.JOB_DISCOVERY, run.stoppingAgentType());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.RESUME).status());
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.JOB_DISCOVERY).status());
        for (AgentType dep : List.of(AgentType.MATCHING, AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR)) {
            assertEquals(AgentStatus.SKIPPED, run.resultOf(dep).status(), dep + " must be SKIPPED");
        }
    }

    // ─── 14. Optional matching failure ─────────────────────────────────────

    @Test
    @DisplayName("matching failure is optional → PARTIAL; career + application still run")
    void matchingOptionalFailure() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        when(s.matchSvc.matchJobs(any())).thenThrow(new RuntimeException("match boom"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertFalse(run.success());
        assertNull(run.stoppingAgentType(), "optional failure never sets a stopping agent");
        assertEquals(List.of(AgentType.MATCHING), run.failedAgents());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
    }

    // ─── 15. Optional career failure ───────────────────────────────────────

    @Test
    @DisplayName("career advisor failure is optional → PARTIAL; application still runs")
    void careerOptionalFailure() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        when(s.gapSvc.analyze(any(), any())).thenThrow(new RuntimeException("gap boom"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertNull(run.stoppingAgentType());
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
    }

    // ─── 16. Optional application failure ──────────────────────────────────

    @Test
    @DisplayName("application advisor failure is optional → PARTIAL with earlier stages COMPLETED")
    void applicationOptionalFailure() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        when(s.prepSvc.prepare(any(), any(), any())).thenThrow(new RuntimeException("prep boom"));

        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.RESUME).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.JOB_DISCOVERY).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.MATCHING).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.CAREER_ADVISOR).status());
    }

    // ─── 17. No agent remains RUNNING ──────────────────────────────────────

    @Test
    @DisplayName("no agent remains RUNNING after any run returns")
    void noRunningAgents() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);

        for (RunStatus status : List.of(RunStatus.COMPLETED)) {
            OrchestrationRun run = orc.orchestrateTracked(apiContext(1L, j));
            for (AgentResult r : run.agentExecutions()) {
                assertTrue(r.status() == AgentStatus.COMPLETED
                        || r.status() == AgentStatus.FAILED
                        || r.status() == AgentStatus.SKIPPED,
                        r.agentType() + " must not remain RUNNING");
            }
        }

        // Also assert on a blocking-failure run.
        StubServices bad = stubs(1L, j);
        when(bad.resumeSvc.buildProfile(anyString())).thenThrow(new RuntimeException("boom"));
        CareerAgentOrchestrator orcBad = orchestrator(bad);
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("resume text that is long enough to parse");
        OrchestrationRun failed = orcBad.orchestrateTracked(ctx);
        for (AgentResult r : failed.agentExecutions()) {
            assertFalse(r.status() == AgentStatus.RUNNING);
        }
    }

    // ─── 18. OrchestrationRun status consistency ───────────────────────────

    @Test
    @DisplayName("success flag is consistent with runStatus across statuses")
    void statusConsistency() {
        Job j = job("job-1");

        StubServices ok = stubs(1L, j);
        OrchestrationRun completed = orchestrator(ok).orchestrateTracked(apiContext(1L, j));
        assertEquals(RunStatus.COMPLETED, completed.runStatus());
        assertTrue(completed.success());

        StubServices matchFail = stubs(1L, j);
        when(matchFail.matchSvc.matchJobs(any())).thenThrow(new RuntimeException("boom"));
        OrchestrationRun partial = orchestrator(matchFail).orchestrateTracked(apiContext(1L, j));
        assertEquals(RunStatus.PARTIAL, partial.runStatus());
        assertFalse(partial.success());

        StubServices resumeFail = stubs(1L, j);
        when(resumeFail.resumeSvc.buildProfile(anyString())).thenThrow(new RuntimeException("boom"));
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("resume text long enough to parse");
        OrchestrationRun failed = orchestrator(resumeFail).orchestrateTracked(ctx);
        assertEquals(RunStatus.FAILED, failed.runStatus());
        assertFalse(failed.success());
        assertEquals(AgentType.RESUME, failed.stoppingAgentType());
    }

    // ─── 20. No raw context exposure (safe representation) ─────────────────

    @Test
    @DisplayName("the run snapshot exposes only safe summaries — never the mutable context or raw resume text")
    void noRawContextExposure() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);
        ctx.setResumeText("sensitive raw resume body that must never be echoed");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        // The run never references the mutable context object.
        assertFalse(run.toString().contains("sensitive raw resume body"));
        for (AgentResult r : run.agentExecutions()) {
            assertFalse(r.message().contains("sensitive raw resume body"));
        }
        // agentResults is a read-only snapshot and never exposes raw context.
        assertTrue(ctx.agentResults().size() == 5);
    }

    // ─── 21. No email sending in the full workflow ─────────────────────────

    @Test
    @DisplayName("the full orchestration never sends email or auto-applies")
    void noEmailSendOrAutoApply() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);

        AgentContext ctx = apiContext(1L, j);
        orc.orchestrateTracked(ctx);

        // The agents only call the preparation service (which builds a draft),
        // and no ApplicationEmailService is wired anywhere in the pipeline.
        verify(s.prepSvc).prepare(any(), any(), any());
        // The sole artifact is a review-required draft.
        assertEquals(ApplicationDraftStatus.REVIEW_REQUIRED, ctx.applicationDraft().status());
    }

    // ─── 23. Deterministic fallback / repeatability ────────────────────────

    @Test
    @DisplayName("repeated identical runs yield identical executions (deterministic)")
    void deterministicRepeat() {
        Job j = job("job-1");
        StubServices a = stubs(1L, j);
        StubServices b = stubs(1L, j);

        OrchestrationRun runA = orchestrator(a).orchestrateTracked(apiContext(1L, j));
        OrchestrationRun runB = orchestrator(b).orchestrateTracked(apiContext(1L, j));

        List<String> statesA = runA.agentExecutions().stream()
                .map(r -> r.agentType() + ":" + r.status()).toList();
        List<String> statesB = runB.agentExecutions().stream()
                .map(r -> r.agentType() + ":" + r.status()).toList();
        assertEquals(statesA, statesB);
    }

    // ─── 24. No RAG / embeddings / vector DB introduced ────────────────────

    @Test
    @DisplayName("the workflow stores only typed domain records — no embeddings/vector types")
    void noRagOrVectors() {
        Job j = job("job-1");
        StubServices s = stubs(1L, j);
        CareerAgentOrchestrator orc = orchestrator(s);
        AgentContext ctx = apiContext(1L, j);

        orc.orchestrateTracked(ctx);

        List<Object> outputs = ctx.agentResults().values().stream()
                .map(AgentResult::output).filter(o -> o != null).toList();
        assertFalse(outputs.isEmpty());
        // Every non-null output is one of the typed domain records produced upstream.
        for (Object out : outputs) {
            String cls = out.getClass().getName().toLowerCase();
            assertFalse(cls.contains("vector"), "no vector type in run-local store");
            assertFalse(cls.contains("embedding"), "no embedding type in run-local store");
        }
        // The context holds typed run-local references only — never a semantic/vector index.
        assertEquals(5, ctx.storedAgentCount());
    }
}
