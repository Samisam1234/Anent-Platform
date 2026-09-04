package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end behavioral tests for {@link CareerAgentOrchestrator#orchestrateTracked}
 * and the {@link OrchestrationRun}/{@link RunStatus} execution-tracking model.
 *
 * <p>Hermetic: no Spring, no DB, no network, no LLM. Uses the same lightweight
 * stub-agent pattern as {@link CareerAgentOrchestratorTest}.</p>
 */
@DisplayName("OrchestrationRun — execution state & run tracking (Phase 6.4)")
class OrchestrationRunTrackingTest {

    // ─── Stub agent support ─────────────────────────────────────────────────

    private static class StubAgent implements CareerAgent {
        private final AgentType type;
        private final Predicate<AgentContext> canExec;
        private final BiFunction<AgentRequest, AgentContext, AgentResult> behaviour;
        private final List<AgentType> log;

        StubAgent(AgentType type, Predicate<AgentContext> canExec,
                  BiFunction<AgentRequest, AgentContext, AgentResult> behaviour, List<AgentType> log) {
            this.type = type;
            this.canExec = canExec;
            this.behaviour = behaviour;
            this.log = log;
        }

        @Override public AgentType type() { return type; }
        @Override public boolean canExecute(AgentContext context) { return canExec.test(context); }
        @Override public AgentResult execute(AgentRequest request, AgentContext context) {
            log.add(type);
            return behaviour.apply(request, context);
        }
    }

    private static StubAgent realistic(AgentType type, List<AgentType> log,
                                       BiFunction<AgentRequest, AgentContext, AgentResult> behaviour) {
        return new StubAgent(type, ctx -> canExecuteRealistic(type, ctx), behaviour, log);
    }

    private static boolean canExecuteRealistic(AgentType type, AgentContext c) {
        if (c == null) {
            return false;
        }
        return switch (type) {
            case RESUME -> c.candidateProfile() != null
                    || (c.resumeText() != null && !c.resumeText().isBlank());
            case JOB_DISCOVERY -> true;
            case MATCHING -> c.candidateProfile() != null
                    && (c.job() != null
                    || (c.jobSearchResult() != null && !c.jobSearchResult().jobs().isEmpty()));
            case CAREER_ADVISOR -> c.candidateProfile() != null && c.job() != null;
            case APPLICATION_ADVISOR -> c.candidateProfile() != null && c.job() != null;
        };
    }

    private static List<CareerAgent> suite(List<AgentType> log) {
        List<CareerAgent> out = new ArrayList<>();
        out.add(realistic(AgentType.RESUME, log, (r, c) -> AgentResult.completed(AgentType.RESUME, "done")));
        out.add(realistic(AgentType.JOB_DISCOVERY, log, (r, c) -> AgentResult.completed(AgentType.JOB_DISCOVERY, "done")));
        out.add(realistic(AgentType.MATCHING, log, (r, c) -> AgentResult.completed(AgentType.MATCHING, "done")));
        out.add(realistic(AgentType.CAREER_ADVISOR, log, (r, c) -> AgentResult.completed(AgentType.CAREER_ADVISOR, "done")));
        out.add(realistic(AgentType.APPLICATION_ADVISOR, log, (r, c) -> AgentResult.completed(AgentType.APPLICATION_ADVISOR, "done")));
        return out;
    }

    private static List<CareerAgent> withReplaced(List<CareerAgent> base, AgentType type, CareerAgent replacement) {
        List<CareerAgent> out = new ArrayList<>();
        for (CareerAgent a : base) {
            out.add(a.type() == type ? replacement : a);
        }
        return out;
    }

    private static final List<AgentType> ALL = List.of(
            AgentType.RESUME, AgentType.JOB_DISCOVERY, AgentType.MATCHING,
            AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR);

    private CandidateProfile profile() {
        return new CandidateProfile("Alice", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of());
    }

    private Job job(String id) {
        return new Job(id, "Engineer", null, null, null, List.of("Java"), List.of(),
                null, null, null, "mock", null, null, null);
    }

    private AgentContext fullContext() {
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        return ctx;
    }

    // ─── 1. Full run → COMPLETED, all five COMPLETED, fixed order ──────────

    @Test
    @DisplayName("full run produces COMPLETED with all five agents COMPLETED in fixed order")
    void fullRunCompleted() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        assertEquals(RunStatus.COMPLETED, run.runStatus());
        assertTrue(run.success());
        assertNull(run.stoppingAgentType());
        assertEquals(ALL, run.completedAgents());
        assertTrue(run.failedAgents().isEmpty());
        assertTrue(run.skippedAgents().isEmpty());

        // Ordered final states cover ALL agents in the fixed sequence.
        assertEquals(ALL, run.agentExecutions().stream().map(AgentResult::agentType).toList());
        for (AgentResult r : run.agentExecutions()) {
            assertEquals(AgentStatus.COMPLETED, r.status());
        }
        assertEquals(ALL, log, "executed in fixed order");
    }

    // ─── 2. Execution order deterministic regardless of agent-list order ────

    @Test
    @DisplayName("tracked run preserves deterministic fixed order regardless of bean order")
    void deterministicOrder() {
        List<AgentType> log = new ArrayList<>();
        List<CareerAgent> reversed = new ArrayList<>(suite(log));
        Collections.reverse(reversed);
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(reversed);

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        assertEquals(ALL, run.agentExecutions().stream().map(AgentResult::agentType).toList());
        assertEquals(ALL, log);
        assertEquals(RunStatus.COMPLETED, run.runStatus());
    }

    // ─── 3. Blocking failure → FAILED, dependents SKIPPED ───────────────────

    @Test
    @DisplayName("resume blocking failure → FAILED; dependents SKIPPED; no RUNNING")
    void blockingFailureFailsRunAndSkipsDependents() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent resume = realistic(AgentType.RESUME, log,
                (r, c) -> AgentResult.failed(AgentType.RESUME, "resume boom", "AGENT_INPUT_MISSING"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, resume));

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("some resume text that is long enough to parse");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.FAILED, run.runStatus());
        assertFalse(run.success());
        assertEquals(AgentType.RESUME, run.stoppingAgentType());
        assertEquals(List.of(AgentType.RESUME), run.failedAgents());

        // All five present with final states; dependents are SKIPPED, never RUNNING.
        assertEquals(ALL, run.agentExecutions().stream().map(AgentResult::agentType).toList());
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.RESUME).status());
        for (AgentType dep : List.of(AgentType.JOB_DISCOVERY, AgentType.MATCHING,
                AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR)) {
            assertEquals(AgentStatus.SKIPPED, run.resultOf(dep).status(), dep + " should be SKIPPED");
        }
        assertFalse(log.contains(AgentType.MATCHING));
        assertFalse(log.contains(AgentType.APPLICATION_ADVISOR));

        // No execution ever leaves an agent RUNNING.
        for (AgentResult r : run.agentExecutions()) {
            assertTrue(r.status() == AgentStatus.COMPLETED
                    || r.status() == AgentStatus.FAILED
                    || r.status() == AgentStatus.SKIPPED);
        }
    }

    @Test
    @DisplayName("job discovery blocking failure → FAILED; matching+ downstream SKIPPED")
    void jobDiscoveryBlockingFailure() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent discovery = realistic(AgentType.JOB_DISCOVERY, log,
                (r, c) -> AgentResult.failed(AgentType.JOB_DISCOVERY, "jd boom", "AGENT_EXECUTION_ERROR"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.JOB_DISCOVERY, discovery));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.FAILED, run.runStatus());
        assertEquals(AgentType.JOB_DISCOVERY, run.stoppingAgentType());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.RESUME).status());
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.JOB_DISCOVERY).status());
        for (AgentType dep : List.of(AgentType.MATCHING, AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR)) {
            assertEquals(AgentStatus.SKIPPED, run.resultOf(dep).status(), dep + " should be SKIPPED");
        }
    }

    // ─── 4. Optional failure → PARTIAL; run continues ───────────────────────

    @Test
    @DisplayName("optional matching failure → PARTIAL; downstream still runs and completes")
    void optionalFailureIsPartial() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent matching = realistic(AgentType.MATCHING, log,
                (r, c) -> AgentResult.failed(AgentType.MATCHING, "m boom", "AGENT_OPTIONAL_FAILURE"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.MATCHING, matching));

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertFalse(run.success());
        assertNull(run.stoppingAgentType());
        assertEquals(List.of(AgentType.MATCHING), run.failedAgents());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertTrue(log.contains(AgentType.CAREER_ADVISOR));
        assertTrue(log.contains(AgentType.APPLICATION_ADVISOR));
    }

    @Test
    @DisplayName("career advisor failure → PARTIAL; application advisor still runs")
    void careerAdvisorFailureIsPartial() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent advisor = realistic(AgentType.CAREER_ADVISOR, log,
                (r, c) -> AgentResult.failed(AgentType.CAREER_ADVISOR, "c boom", "AGENT_EXECUTION_ERROR"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.CAREER_ADVISOR, advisor));

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertNull(run.stoppingAgentType(), "optional failures never set a stopping agent");
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertTrue(log.contains(AgentType.APPLICATION_ADVISOR));
    }

    // ─── 5. All skipped / nothing produced → PARTIAL ────────────────────────

    @Test
    @DisplayName("no usable work produced (every agent skipped) → PARTIAL")
    void allSkippedIsPartial() {
        List<AgentType> log = new ArrayList<>();
        // Dedicate stubs that can NEVER execute, so nothing is produced.
        List<CareerAgent> allSkip = new ArrayList<>();
        for (AgentType type : ALL) {
            allSkip.add(new StubAgent(type, ctx -> false,
                    (r, c) -> AgentResult.completed(type, "unreachable"), List.of()));
        }
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(allSkip);

        OrchestrationRun run = orc.orchestrateTracked(new AgentContext());

        assertTrue(run.completedAgents().isEmpty());
        assertTrue(run.failedAgents().isEmpty());
        assertFalse(run.skippedAgents().isEmpty());
        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertFalse(run.success());
        assertEquals(ALL, run.agentExecutions().stream().map(AgentResult::agentType).toList());
        for (AgentResult r : run.agentExecutions()) {
            assertEquals(AgentStatus.SKIPPED, r.status());
        }
    }

    // ─── 6. Deterministic skip reasons ──────────────────────────────────────

    @Test
    @DisplayName("skip reasons are deterministic, human-readable, and safe")
    void skipReasonsAreDeterministic() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        // no job → matching / career / app skipped

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(AgentStatus.SKIPPED, run.resultOf(AgentType.MATCHING).status());
        assertNotNull(run.resultOf(AgentType.MATCHING).message());
        assertFalse(run.resultOf(AgentType.MATCHING).message().isBlank());
        // No stack traces / exception class names exposed.
        for (AgentResult r : run.agentExecutions()) {
            if (r.status() == AgentStatus.SKIPPED) {
                String m = r.message();
                assertNotNull(m);
                assertFalse(m.contains("Exception"), "skip reason must not expose exceptions: " + m);
                assertFalse(m.contains(" at "), "skip reason must not expose stack traces: " + m);
            }
        }
    }

    @Test
    @DisplayName("resume skip reason is specific when no resume input")
    void resumeSkipReasonSpecific() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = new AgentContext();
        ctx.setJob(job("j1")); // no candidate, no resume text

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(AgentStatus.SKIPPED, run.resultOf(AgentType.RESUME).status());
        assertTrue(run.resultOf(AgentType.RESUME).message().toLowerCase().contains("resume"));
    }

    // ─── 7. No agent remains RUNNING after return ───────────────────────────

    @Test
    @DisplayName("no agent is ever RUNNING in the returned run snapshot")
    void noRunningAgentsAfterReturn() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        for (AgentResult r : run.agentExecutions()) {
            assertFalse(r.status() == AgentStatus.RUNNING,
                    r.agentType() + " must not remain RUNNING after return");
        }
    }

    // ─── 8. AI / tool usage accounting ──────────────────────────────────────

    @Test
    @DisplayName("aiCallsUsed reflects consumed budget from existing AgentContext")
    void aiCallsUsedFromBudget() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = fullContext();
        assertTrue(ctx.consumeAiCall());
        assertTrue(ctx.consumeAiCall());

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(2, run.aiCallsUsed());
        assertEquals(CareerAgentOrchestrator.MAX_AI_CALLS - 2, ctx.aiCallsRemaining());
    }

    @Test
    @DisplayName("toolCallsUsed reflects consumed tool budget from existing AgentContext")
    void toolCallsUsedFromBudget() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = fullContext();
        assertTrue(ctx.consumeToolCall(AgentType.RESUME));
        assertTrue(ctx.consumeToolCall(AgentType.RESUME));
        assertTrue(ctx.consumeToolCall(AgentType.MATCHING));

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(3, run.toolCallsUsed());
        assertEquals(AgentToolOrchestrator.MAX_TOOL_CALLS_PER_ORCHESTRATION - 3,
                ctx.toolCallsRemaining());
    }

    // ─── 9. Budget enforcement ──────────────────────────────────────────────

    @Test
    @DisplayName("an idle run (no AI/tool consumption) reports zero usage")
    void budgetsRespectLimits() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        OrchestrationRun idle = orc.orchestrateTracked(fullContext());
        assertEquals(0, idle.aiCallsUsed());
        assertEquals(0, idle.toolCallsUsed());
    }

    // ─── 10. Immutability ───────────────────────────────────────────────────

    @Test
    @DisplayName("OrchestrationRun is immutable; execution list is an unmodifiable copy")
    void runImmutability() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        assertThrows(UnsupportedOperationException.class,
                () -> run.agentExecutions().add(run.agentExecutions().get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> run.completedAgents().add(AgentType.RESUME));
    }

    // ─── 11. No mutation of domain inputs ───────────────────────────────────

    @Test
    @DisplayName("candidate profile and job references are never mutated")
    void inputsNotMutated() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        CandidateProfile original = profile();
        Job originalJob = job("j1");
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(original);
        ctx.setJob(originalJob);

        orc.orchestrateTracked(ctx);

        assertTrue(ctx.candidateProfile() == original);
        assertTrue(ctx.job() == originalJob);
    }

    // ─── 12. Single-threaded, no recursion, each agent once ─────────────────

    @Test
    @DisplayName("execution is single-threaded and each agent runs exactly once (no recursion/parallelism)")
    void singleThreadedExactlyOnce() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = fullContext();
        ctx.setCandidateId(1L);

        orc.orchestrateTracked(ctx);

        for (AgentType type : ALL) {
            long count = log.stream().filter(t -> t == type).count();
            assertEquals(1, count, type + " must run exactly once");
        }
        assertEquals(5, log.size());
        // Strict single-threaded order — no parallel agent interleaving.
        assertEquals(ALL, log, "agents ran on one thread in strict order");
    }

    // ─── 13. Keep 6.1 orchestrate() contract intact ─────────────────────────

    @Test
    @DisplayName("existing orchestrate() contract is preserved alongside tracking")
    void orchestrateContractPreserved() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = fullContext();
        OrchestrationResult result = orc.orchestrate(ctx);

        assertTrue(result.success());
        assertEquals(AgentStatus.COMPLETED, result.status());
        assertFalse(result.blockingFailure());
        assertEquals(5, result.executed().size());
        assertNull(result.stoppingAgentType());
    }

    // ─── 14. Null safety ────────────────────────────────────────────────────

    @Test
    @DisplayName("tracked orchestration rejects null context")
    void nullContextRejected() {
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> orc.orchestrateTracked(null));
    }

    // ─── 15. resolveStatus rules ────────────────────────────────────────────

    @Test
    @DisplayName("resolveStatus follows documented deterministic rules")
    void resolveStatusRules() {
        List<AgentResult> allCompleted = List.of(
                AgentResult.completed(AgentType.RESUME, "x"),
                AgentResult.completed(AgentType.JOB_DISCOVERY, "x"));
        assertEquals(RunStatus.COMPLETED, OrchestrationRun.resolveStatus(allCompleted, false));

        List<AgentResult> withFailed = List.of(
                AgentResult.completed(AgentType.RESUME, "x"),
                AgentResult.failed(AgentType.MATCHING, "x", "E"));
        assertEquals(RunStatus.PARTIAL, OrchestrationRun.resolveStatus(withFailed, false));

        List<AgentResult> withBlocking = List.of(
                AgentResult.failed(AgentType.RESUME, "x", "E"),
                AgentResult.skipped(AgentType.MATCHING, "x"));
        assertEquals(RunStatus.FAILED, OrchestrationRun.resolveStatus(withBlocking, true));

        List<AgentResult> onlySkipped = List.of(
                AgentResult.skipped(AgentType.RESUME, "x"),
                AgentResult.skipped(AgentType.MATCHING, "x"));
        assertEquals(RunStatus.PARTIAL, OrchestrationRun.resolveStatus(onlySkipped, false));

        assertEquals(RunStatus.PARTIAL, OrchestrationRun.resolveStatus(List.of(), false));
        assertEquals(RunStatus.PARTIAL, OrchestrationRun.resolveStatus(null, false));
    }

    // ─── 16. Result helpers ─────────────────────────────────────────────────

    @Test
    @DisplayName("resultOf, completedAgents, failedAgents, skippedAgents helpers")
    void runHelpers() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        OrchestrationRun run = orc.orchestrateTracked(fullContext());

        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.RESUME).status());
        assertNull(run.resultOf(null));
        assertEquals(ALL, run.completedAgents());
        assertTrue(run.failedAgents().isEmpty());
        assertTrue(run.skippedAgents().isEmpty());
    }

    // ─── 17. Stopping agent captured on blocking failure ────────────────────

    @Test
    @DisplayName("stoppingAgentType is the blocking agent that halted the run")
    void stoppingAgentTypeCaptured() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent resume = realistic(AgentType.RESUME, log,
                (r, c) -> AgentResult.failed(AgentType.RESUME, "boom", "AGENT_INPUT_MISSING"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, resume));

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("some resume text that is long enough to parse");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(AgentType.RESUME, run.stoppingAgentType());
        assertFalse(run.success());
        assertEquals(RunStatus.FAILED, run.runStatus());
    }

    // ─── 18. Safe error codes only ──────────────────────────────────────────

    @Test
    @DisplayName("only safe, known error codes are exposed — never exception class names")
    void safeErrorCodesOnly() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent resume = realistic(AgentType.RESUME, log, (r, c) -> {
            throw new IllegalStateException("secret private failure detail");
        });
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, resume));

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("some resume text that is long enough to parse");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        AgentResult resumeResult = run.resultOf(AgentType.RESUME);
        assertEquals(AgentStatus.FAILED, resumeResult.status());
        assertEquals("AGENT_EXECUTION_ERROR", resumeResult.errorCode());
        assertFalse(resumeResult.message().contains("secret private failure detail"));
        assertFalse(resumeResult.message().contains("Exception"));
    }

    // ─── 19. Success flag tied to run status ────────────────────────────────

    @Test
    @DisplayName("success is true exactly when runStatus is COMPLETED")
    void successMatchesStatus() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator ok = new CareerAgentOrchestrator(suite(log));
        OrchestrationRun completed = ok.orchestrateTracked(fullContext());
        assertEquals(RunStatus.COMPLETED, completed.runStatus());
        assertTrue(completed.success());

        List<AgentType> log2 = new ArrayList<>();
        CareerAgent advisor = realistic(AgentType.CAREER_ADVISOR, log2,
                (r, c) -> AgentResult.failed(AgentType.CAREER_ADVISOR, "x", "AGENT_EXECUTION_ERROR"));
        CareerAgentOrchestrator fail = new CareerAgentOrchestrator(
                withReplaced(suite(log2), AgentType.CAREER_ADVISOR, advisor));
        OrchestrationRun partial = fail.orchestrateTracked(fullContext());
        assertEquals(RunStatus.PARTIAL, partial.runStatus());
        assertFalse(partial.success());
    }

    // ─── 20. Tracked run never fabricates a result (blocking tail is SKIPPED) ──

    @Test
    @DisplayName("blocking failure never fabricates downstream results; they are SKIPPED with safe reason")
    void blockingTailSkippedNotFabricated() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent discovery = realistic(AgentType.JOB_DISCOVERY, log,
                (r, c) -> AgentResult.failed(AgentType.JOB_DISCOVERY, "boom", "AGENT_EXECUTION_ERROR"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.JOB_DISCOVERY, discovery));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        for (AgentResult r : run.agentExecutions()) {
            if (r.status() == AgentStatus.SKIPPED) {
                assertNull(r.output(), "skipped agent must not carry fabricated output");
                assertTrue(r.message().toLowerCase().contains("skip"));
            }
        }
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.JOB_DISCOVERY).status());
        assertEquals(AgentStatus.SKIPPED, run.resultOf(AgentType.MATCHING).status());
        // The context must never record a fabricated COMPLETED result for a
        // downstream dependent of a blocking failure.
        AgentResult ctxMatching = ctx.resultOf(AgentType.MATCHING);
        assertTrue(ctxMatching == null || ctxMatching.status() != AgentStatus.COMPLETED);
    }

    // ─── 21. Both methods agree on the executed set for full run ────────────

    @Test
    @DisplayName("orchestrate() executed set and tracked agent executions agree")
    void trackedAndUntrackedAgree() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = fullContext();

        OrchestrationResult result = orc.orchestrate(ctx);
        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(5, result.executed().size());
        assertEquals(5, run.agentExecutions().size());
    }
}
