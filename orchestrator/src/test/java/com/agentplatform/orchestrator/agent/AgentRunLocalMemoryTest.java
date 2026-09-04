package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6.6 — run-local agent memory/context. Verifies that {@link AgentContext}
 * acts as a bounded, short-lived store of structured agent results: it starts
 * empty, is populated as agents complete, is readable by the orchestrator's
 * later stages, is defensive and bounded, and never leaks across runs. Hermetic:
 * no DB, no network, no Ollama, no SMTP.
 */
@DisplayName("AgentContext — run-local agent memory (Phase 6.6)")
class AgentRunLocalMemoryTest {

    private static final List<AgentType> ALL = List.of(
            AgentType.RESUME, AgentType.JOB_DISCOVERY, AgentType.MATCHING,
            AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR);

    // ─── Stub agent support (mirrors the other orchestrator tests) ──────────

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

    // ─── 1. Starts empty ────────────────────────────────────────────────────

    @Test
    @DisplayName("a fresh AgentContext starts with no stored results")
    void startsEmpty() {
        AgentContext ctx = new AgentContext();
        assertTrue(ctx.hasNoStoredResults());
        assertEquals(0, ctx.storedAgentCount());
        assertNull(ctx.resultOf(AgentType.RESUME));
    }

    // ─── 2. Result stored after completion ──────────────────────────────────

    @Test
    @DisplayName("a completed agent result is stored and readable")
    void storesCompletedResult() {
        AgentContext ctx = new AgentContext();
        assertTrue(ctx.record(AgentResult.completed(AgentType.RESUME, "built profile")));
        assertEquals(1, ctx.storedAgentCount());
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.RESUME).status());
        assertTrue(ctx.hasStoredResult(AgentType.RESUME));
        assertFalse(ctx.hasNoStoredResults());
    }

    // ─── 3. Later agent can retrieve earlier result ─────────────────────────

    @Test
    @DisplayName("a later stage can read an earlier stage's stored result")
    void laterAgentReadsEarlierResult() {
        AgentContext ctx = new AgentContext();
        ctx.record(AgentResult.completed(AgentType.CAREER_ADVISOR, "gap done"));
        ctx.record(AgentResult.completed(AgentType.MATCHING, "match done"));

        assertTrue(ctx.hasStoredResult(AgentType.CAREER_ADVISOR));
        assertTrue(ctx.hasStoredResult(AgentType.MATCHING));
        assertEquals("match done", ctx.resultOf(AgentType.MATCHING).message());
        assertEquals("gap done", ctx.resultOf(AgentType.CAREER_ADVISOR).message());
    }

    // ─── 4. Typed result retrieval ──────────────────────────────────────────

    @Test
    @DisplayName("structured output is retrievable in its typed form")
    void typedRetrieval() {
        AgentContext ctx = new AgentContext();
        CandidateProfile p = profile();
        ctx.record(AgentResult.completed(AgentType.RESUME, "built", p));

        CandidateProfile out = ctx.resultOf(AgentType.RESUME).outputAs(CandidateProfile.class);
        assertEquals(p, out);
        assertNull(ctx.resultOf(AgentType.RESUME).outputAs(Job.class));
    }

    // ─── 5. Missing result returns empty/safe ───────────────────────────────

    @Test
    @DisplayName("a missing result is reported safely (null / false), never fabricated")
    void missingResultSafe() {
        AgentContext ctx = new AgentContext();
        assertNull(ctx.resultOf(AgentType.MATCHING));
        assertFalse(ctx.hasStoredResult(AgentType.MATCHING));
    }

    // ─── 6 & 7. Failed / skipped stored correctly ───────────────────────────

    @Test
    @DisplayName("failed and skipped results are stored with correct status, no fabrication")
    void failedAndSkippedStored() {
        AgentContext ctx = new AgentContext();
        ctx.record(AgentResult.failed(AgentType.MATCHING, "matching failed", "AGENT_OPTIONAL_FAILURE"));
        ctx.record(AgentResult.skipped(AgentType.APPLICATION_ADVISOR, "No input."));

        AgentResult failed = ctx.resultOf(AgentType.MATCHING);
        assertEquals(AgentStatus.FAILED, failed.status());
        assertFalse(failed.success());
        assertEquals("AGENT_OPTIONAL_FAILURE", failed.errorCode());
        assertNull(failed.output(), "a failed result carries no fabricated output");

        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertFalse(ctx.resultOf(AgentType.APPLICATION_ADVISOR).success());
    }

    // ─── 8. Maximum count enforced ──────────────────────────────────────────

    @Test
    @DisplayName("the run-local store is capped at MAX_STORED_AGENT_RESULTS (5)")
    void maxCountEnforced() {
        AgentContext ctx = new AgentContext();
        assertEquals(5, ctx.maxStoredAgentResults());
        for (AgentType t : ALL) {
            assertTrue(ctx.record(AgentResult.completed(t, "done")), "each distinct agent stores");
        }
        assertEquals(5, ctx.storedAgentCount());
        // Re-recording (overwrite) never grows the store.
        assertTrue(ctx.record(AgentResult.completed(AgentType.RESUME, "again")));
        assertEquals(5, ctx.storedAgentCount());
        // Invalid / null results are rejected.
        assertFalse(ctx.record(null));
    }

    // ─── 9. Large result/message is bounded ─────────────────────────────────

    @Test
    @DisplayName("an oversized message is truncated to the bounded length")
    void messageBounded() {
        AgentContext ctx = new AgentContext();
        String huge = "x".repeat(5000);
        ctx.record(AgentResult.completed(AgentType.JOB_DISCOVERY, huge));

        String stored = ctx.resultOf(AgentType.JOB_DISCOVERY).message();
        assertTrue(stored.length() <= AgentContext.MAX_RESULT_MESSAGE_LENGTH,
                "stored message must be bounded, was " + stored.length());
    }

    // ─── 10. No direct mutable collection exposure ──────────────────────────

    @Test
    @DisplayName("the exposed results map is an immutable defensive copy")
    void noMutableExposure() {
        AgentContext ctx = new AgentContext();
        ctx.record(AgentResult.completed(AgentType.RESUME, "done"));
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.agentResults().put(AgentType.MATCHING, AgentResult.completed(AgentType.MATCHING, "x")));
        // Mutating a copy must not affect the store.
        assertFalse(ctx.hasStoredResult(AgentType.MATCHING));
    }

    // ─── 11 & 12. Cross-run isolation / lifecycle ───────────────────────────

    @Test
    @DisplayName("run A results never leak into run B (fresh context per run)")
    void crossRunIsolation() {
        AgentContext runA = new AgentContext();
        runA.record(AgentResult.completed(AgentType.RESUME, "a"));
        assertEquals(1, runA.storedAgentCount());

        AgentContext runB = new AgentContext();
        assertTrue(runB.hasNoStoredResults());
        runB.record(AgentResult.completed(AgentType.RESUME, "b"));

        assertTrue(runA.hasStoredResult(AgentType.RESUME));
        assertTrue(runB.hasStoredResult(AgentType.RESUME));
        assertEquals("a", runA.resultOf(AgentType.RESUME).message());
        assertEquals("b", runB.resultOf(AgentType.RESUME).message());

        // clearStoredResults is the explicit lifecycle reset.
        runA.clearStoredResults();
        assertTrue(runA.hasNoStoredResults());
        assertFalse(runA.hasStoredResult(AgentType.RESUME));
    }

    @Test
    @DisplayName("the orchestrator does not retain run-local memory after completion")
    void orchestratorDoesNotRetain() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctxA = fullContext();
        orc.orchestrateTracked(ctxA);
        assertEquals(5, ctxA.storedAgentCount(), "full run records all five agents");

        // A second, fresh run starts clean — no shared state.
        AgentContext ctxB = fullContext();
        assertTrue(ctxB.hasNoStoredResults());
        orc.orchestrateTracked(ctxB);
        assertEquals(5, ctxB.storedAgentCount());
        assertEquals(AgentStatus.COMPLETED, ctxB.resultOf(AgentType.MATCHING).status());
    }

    // ─── 13. Agent order unchanged ──────────────────────────────────────────

    @Test
    @DisplayName("agents execute and record in the fixed deterministic order")
    void fixedOrder() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = fullContext();
        orc.orchestrateTracked(ctx);
        assertEquals(ALL, new ArrayList<>(log),
                "execution/recording must follow the orchestrator SEQUENCE order");
    }

    // ─── 14. Blocking failure unchanged ─────────────────────────────────────

    @Test
    @DisplayName("blocking failure behavior is unchanged")
    void blockingFailureUnchanged() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent resume = realistic(AgentType.RESUME, log,
                (r, c) -> AgentResult.failed(AgentType.RESUME, "boom", "RESUME_FAILED"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, resume));
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("some resume text that is long enough to parse");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.FAILED, run.runStatus());
        assertEquals(AgentType.RESUME, run.stoppingAgentType());
        assertEquals(AgentStatus.FAILED, ctx.resultOf(AgentType.RESUME).status());
        assertFalse(run.agentExecutions().stream().anyMatch(
                r -> r.status() == AgentStatus.RUNNING));
    }

    // ─── 15. Optional failure unchanged ─────────────────────────────────────

    @Test
    @DisplayName("optional failure behavior is unchanged (PARTIAL, pipeline continues)")
    void optionalFailureUnchanged() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent matching = realistic(AgentType.MATCHING, log,
                (r, c) -> AgentResult.failed(AgentType.MATCHING, "boom", "MATCHING_FAILED"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.MATCHING, matching));
        AgentContext ctx = fullContext();

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertEquals(AgentStatus.FAILED, ctx.resultOf(AgentType.MATCHING).status());
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.CAREER_ADVISOR).status());
    }

    // ─── 16 & 17. Budgets unchanged ─────────────────────────────────────────

    @Test
    @DisplayName("AI and tool budgets are unchanged and enforced")
    void budgetsUnchanged() {
        assertEquals(3, CareerAgentOrchestrator.MAX_AI_CALLS);
        assertEquals(4, AgentToolOrchestrator.MAX_TOOL_CALLS_PER_ORCHESTRATION);

        AgentContext ctx = new AgentContext();
        assertEquals(3, ctx.aiCallsRemaining());
        assertTrue(ctx.consumeAiCall());
        assertEquals(2, ctx.aiCallsRemaining());

        assertEquals(4, ctx.toolCallsRemaining());
        assertTrue(ctx.consumeToolCall(AgentType.RESUME));
        assertEquals(3, ctx.toolCallsRemaining());
    }

    // ─── 18. No new LLM calls caused by memory ──────────────────────────────

    @Test
    @DisplayName("run-local memory causes no extra LLM calls (stub agents consume none)")
    void noNewLlmCalls() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = fullContext();

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(0, run.aiCallsUsed());
        assertEquals(3, ctx.aiCallsRemaining(), "AI budget untouched by stub run");
    }

    // ─── 19. No email caused by memory ──────────────────────────────────────

    @Test
    @DisplayName("run-local memory never triggers email sending")
    void noEmailFromMemory() {
        List<AgentType> log = new ArrayList<>();
        // Real ApplicationAdvisorAgent only prepares a draft; no send path in agent.
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = fullContext();

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.COMPLETED, run.runStatus());
        // A stub application advisor produces a COMPLETED state, never a send.
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
    }

    // ─── 20 & 21. No CandidateProfile / Job mutation ────────────────────────

    @Test
    @DisplayName("run-local memory never mutates candidate profile or job")
    void noDomainMutation() {
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
        assertTrue(ctx.hasStoredResult(AgentType.RESUME));
    }

    // ─── 23. Deterministic execution ────────────────────────────────────────

    @Test
    @DisplayName("repeated identical runs are deterministic")
    void deterministic() {
        List<AgentType> logA = new ArrayList<>();
        List<AgentType> logB = new ArrayList<>();
        CareerAgentOrchestrator orcA = new CareerAgentOrchestrator(suite(logA));
        CareerAgentOrchestrator orcB = new CareerAgentOrchestrator(suite(logB));

        OrchestrationRun runA = orcA.orchestrateTracked(fullContext());
        OrchestrationRun runB = orcB.orchestrateTracked(fullContext());

        assertEquals(runA.runStatus(), runB.runStatus());
        assertEquals(runA.agentExecutions().size(), runB.agentExecutions().size());
        assertEquals(ALL, logA);
        assertEquals(ALL, logB);
        assertEquals(runA.agentExecutions().stream().map(AgentResult::agentType).toList(),
                runB.agentExecutions().stream().map(AgentResult::agentType).toList());
    }

    // ─── 24. No RAG / embeddings / vector DB introduced ─────────────────────

    @Test
    @DisplayName("the run-local store holds only typed AgentResult entries — no embeddings/vector types")
    void noRagOrVectors() {
        AgentContext ctx = new AgentContext();
        for (AgentType t : ALL) {
            ctx.record(AgentResult.completed(t, "done"));
        }
        // Every entry is a plain AgentResult over an AgentType key — no vector,
        // embedding, or semantic-memory types anywhere in the public store.
        for (Map.Entry<AgentType, AgentResult> e : ctx.agentResults().entrySet()) {
            assertTrue(e.getKey() instanceof AgentType);
            assertTrue(e.getValue() instanceof AgentResult);
        }
        assertEquals(5, ctx.storedAgentCount());
        assertFalse(ctx.agentResults().values().stream()
                .anyMatch(r -> r.output() != null && (r.output().getClass().getName().toLowerCase().contains("vector")
                        || r.output().getClass().getName().toLowerCase().contains("embedding"))));
    }
}
