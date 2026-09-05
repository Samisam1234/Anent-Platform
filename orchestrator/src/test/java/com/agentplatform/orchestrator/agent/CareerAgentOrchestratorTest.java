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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end behavioral tests for {@link CareerAgentOrchestrator}, using
 * lightweight stub agents that model the real precondition semantics so the
 * sequential flow and its failure/skip rules can be verified precisely and
 * hermetic (no Spring, no DB, no LLM).
 */
@DisplayName("CareerAgentOrchestrator — bounded sequential orchestration")
class CareerAgentOrchestratorTest {

    // ─── Stub agent support ─────────────────────────────────────────────────

    /** A stub agent with controllable precondition and behaviour, logging every execution. */
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
        @Override public boolean canExecute(AgentContext context) {
            return canExec.test(context);
        }
        @Override public AgentResult execute(AgentRequest request, AgentContext context) {
            log.add(type);
            return behaviour.apply(request, context);
        }
    }

    /**
     * Builds a stub whose {@code canExecute} mirrors the real agent preconditions
     * in this pipeline, returning {@code resultSupplier} when it executes.
     */
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

    // ─── 9. Normal sequential execution ─────────────────────────────────────

    @Test
    @DisplayName("normal sequential execution runs all agents in order")
    void normalSequentialExecution() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationResult result = orc.orchestrate(ctx);

        assertTrue(result.success());
        assertEquals(List.of(AgentType.RESUME, AgentType.JOB_DISCOVERY, AgentType.MATCHING,
                AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR), log);
        assertEquals(5, result.executed().size());
    }

    // ─── 17. Deterministic execution order ──────────────────────────────────

    @Test
    @DisplayName("execution order is deterministic regardless of agent list order")
    void deterministicOrder() {
        List<AgentType> log = new ArrayList<>();
        List<CareerAgent> reversed = new ArrayList<>(suite(log));
        Collections.reverse(reversed);
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(reversed);

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationResult result = orc.orchestrate(ctx);

        assertTrue(result.success());
        assertEquals(List.of(AgentType.RESUME, AgentType.JOB_DISCOVERY, AgentType.MATCHING,
                AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR), log);
    }

    // ─── 10. Missing candidate → safe skip ─────────────────────────────────

    @Test
    @DisplayName("missing candidate profile safely skips dependent agents")
    void missingCandidateSkipsDependents() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = new AgentContext();
        ctx.setJob(job("j1")); // no candidate, no resume text

        OrchestrationResult result = orc.orchestrate(ctx);

        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.RESUME).status());
        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.MATCHING).status());
        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertFalse(result.success(), "not all stages completed when some are skipped");
        // Only job discovery runs; everything candidate-dependent is skipped.
        assertEquals(List.of(AgentType.JOB_DISCOVERY), log);
    }

    // ─── 11. Missing job → safe skip ───────────────────────────────────────

    @Test
    @DisplayName("missing job safely skips matching and advisories")
    void missingJobSkipsDependents() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        // no job, no job search results

        OrchestrationResult result = orc.orchestrate(ctx);

        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.RESUME).status());
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.JOB_DISCOVERY).status());
        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.MATCHING).status());
        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals(AgentStatus.SKIPPED, ctx.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertFalse(result.success());
        assertFalse(log.contains(AgentType.CAREER_ADVISOR));
        assertFalse(log.contains(AgentType.APPLICATION_ADVISOR));
    }

    // ─── 12. Required-agent failure stops dependents ────────────────────────

    @Test
    @DisplayName("resume failure aborts the pipeline (blocking)")
    void blockingFailureStopsDependents() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent resume = realistic(AgentType.RESUME, log,
                (r, c) -> AgentResult.failed(AgentType.RESUME, "boom", "RESUME_FAILED"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, resume));

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("some resume text that is long enough to parse");

        OrchestrationResult result = orc.orchestrate(ctx);

        assertFalse(result.success());
        assertTrue(result.blockingFailure());
        assertEquals(AgentStatus.FAILED, ctx.resultOf(AgentType.RESUME).status());
        assertFalse(log.contains(AgentType.MATCHING));
        assertFalse(log.contains(AgentType.CAREER_ADVISOR));
        assertFalse(log.contains(AgentType.APPLICATION_ADVISOR));
    }

    @Test
    @DisplayName("job discovery failure is blocking")
    void jobDiscoveryFailureIsBlocking() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent discovery = realistic(AgentType.JOB_DISCOVERY, log,
                (r, c) -> AgentResult.failed(AgentType.JOB_DISCOVERY, "boom", "JD_FAILED"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.JOB_DISCOVERY, discovery));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());

        OrchestrationResult result = orc.orchestrate(ctx);

        assertFalse(result.success());
        assertTrue(result.blockingFailure());
        assertFalse(log.contains(AgentType.MATCHING));
    }

    // ─── 13. Optional-agent failure does not crash orchestrator ─────────────

    @Test
    @DisplayName("matching failure is non-blocking and does not crash the run")
    void optionalFailureContinues() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent matching = realistic(AgentType.MATCHING, log,
                (r, c) -> AgentResult.failed(AgentType.MATCHING, "boom", "MATCHING_FAILED"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.MATCHING, matching));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationResult result = orc.orchestrate(ctx);

        assertEquals(AgentStatus.FAILED, ctx.resultOf(AgentType.MATCHING).status());
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.APPLICATION_ADVISOR).status());
        assertFalse(result.blockingFailure());
        assertTrue(log.contains(AgentType.CAREER_ADVISOR));
        assertTrue(log.contains(AgentType.APPLICATION_ADVISOR));
    }

    // ─── 16. No agent recursively invokes itself ───────────────────────────

    @Test
    @DisplayName("each agent executes exactly once — no recursion or self-invocation")
    void noRecursiveInvocation() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        orc.orchestrate(ctx);

        for (AgentType type : AgentType.values()) {
            long count = log.stream().filter(t -> t == type).count();
            assertEquals(1, count, type + " executed " + count + " times (must be exactly once)");
        }
        assertEquals(5, log.size());
    }

    // ─── 14. No infinite loops ─────────────────────────────────────────────

    @Test
    @DisplayName("orchestration always terminates")
    void alwaysTerminates() {
        // Even a pathological agent returning a fresh result cannot cause looping:
        // the fixed sequence is bounded.
        List<AgentType> log = new ArrayList<>();
        CareerAgent looping = realistic(AgentType.MATCHING, log,
                (r, c) -> AgentResult.completed(AgentType.MATCHING, "iter"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.MATCHING, looping));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationResult result = orc.orchestrate(ctx);

        assertTrue(result.success());
        assertEquals(1, log.stream().filter(t -> t == AgentType.MATCHING).count());
    }

    // ─── 15. Maximum agent count enforced ──────────────────────────────────

    @Test
    @DisplayName("maximum agent count is enforced")
    void maxAgentCountEnforced() {
        assertEquals(5, CareerAgentOrchestrator.MAX_AGENTS);
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        orc.orchestrate(ctx);
        assertTrue(log.size() <= CareerAgentOrchestrator.MAX_AGENTS);
    }

    // ─── 18. No email sending during orchestration ─────────────────────────

    @Test
    @DisplayName("no email sending occurs during orchestration")
    void noEmailSending() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        orc.orchestrate(ctx);
        // The application advisor produces drafts only; no send result exists.
        assertFalse(AgentType.APPLICATION_ADVISOR.name().contains("SEND"));
        assertTrue(ctx.applicationDraft() == null
                || ctx.applicationDraft().status() != null);
    }

    // ─── 19/20. Candidate profile and job are not mutated ───────────────────

    @Test
    @DisplayName("candidate profile and job are never mutated by orchestration")
    void inputsNotMutated() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        CandidateProfile original = profile();
        Job originalJob = job("j1");
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(original);
        ctx.setJob(originalJob);
        orc.orchestrate(ctx);

        assertTrue(ctx.candidateProfile() == original);
        assertTrue(ctx.job() == originalJob);
    }

    // ─── 21. Existing domain models reused ─────────────────────────────────

    @Test
    @DisplayName("the orchestrator reuses existing domain model records")
    void reusesExistingModels() {
        List<AgentType> log = new ArrayList<>();
        CandidateProfile profile = profile();
        Job j = job("j1");
        CapturingAgent capturing = new CapturingAgent(AgentType.RESUME, log);
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, capturing));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile);
        ctx.setJob(j);
        orc.orchestrate(ctx);
        assertTrue(capturing.seenProfile == profile);
        assertTrue(capturing.seenJob == j);
    }

    /** A stub that records the domain references it was given. */
    private static class CapturingAgent extends StubAgent {
        CandidateProfile seenProfile;
        Job seenJob;

        CapturingAgent(AgentType type, List<AgentType> log) {
            super(type, ctx -> true, (r, c) -> AgentResult.completed(type, "done"), log);
        }

        @Override
        public AgentResult execute(AgentRequest request, AgentContext context) {
            this.seenProfile = context.candidateProfile();
            this.seenJob = context.job();
            return super.execute(request, context);
        }
    }

    // ─── 22. Ollama unavailable does not break deterministic workflow ───────

    @Test
    @DisplayName("runs cleanly without any LLM dependency")
    void runsWithoutOllama() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        OrchestrationResult result = orc.orchestrate(ctx);
        assertTrue(result.success());
    }

    // ─── Constructor validation ─────────────────────────────────────────────

    @Test
    @DisplayName("orchestrator requires all five agent types and a non-empty list")
    void constructorValidates() {
        List<CareerAgent> onlyOne = new ArrayList<>();
        onlyOne.add(realistic(AgentType.RESUME, new ArrayList<>(),
                (r, c) -> AgentResult.completed(AgentType.RESUME, "done")));
        assertThrows(IllegalArgumentException.class, () -> new CareerAgentOrchestrator(onlyOne));
        assertThrows(IllegalArgumentException.class, () -> new CareerAgentOrchestrator(null));
        assertThrows(IllegalArgumentException.class, () -> new CareerAgentOrchestrator(List.of()));
    }

    // ─── Timing and error code propagation ─────────────────────────────────

    @Test
    @DisplayName("agent results include timing information")
    void agentResultsIncludeTiming() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        for (AgentType type : AgentType.values()) {
            AgentResult result = run.resultOf(type);
            assertNotNull(result, () -> "Result for " + type + " should not be null");
            assertNotNull(result.startedAt(), () -> "startedAt should be set for " + type);
            assertNotNull(result.completedAt(), () -> "completedAt should be set for " + type);
            assertTrue(result.durationMs() >= 0, () -> "durationMs should be non-negative for " + type);
        }
    }

    @Test
    @DisplayName("skipped agents have timing information")
    void skippedAgentsHaveTiming() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        // No candidate profile - RESUME and dependents will be skipped
        ctx.setJob(job("j1"));

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        AgentResult resumeResult = run.resultOf(AgentType.RESUME);
        assertEquals(AgentStatus.SKIPPED, resumeResult.status());
        assertNotNull(resumeResult.startedAt());
        assertNotNull(resumeResult.completedAt());
        assertTrue(resumeResult.durationMs() >= 0);
    }

    @Test
    @DisplayName("orchestration completes even when AI reasoning fails in agents")
    void orchestrationCompletesAfterReasoningFailure() {
        List<AgentType> log = new ArrayList<>();
        // Simulate an agent that fails during reasoning (returns FAILED with error code)
        CareerAgent failingAdvisor = realistic(AgentType.CAREER_ADVISOR, log,
                (r, c) -> AgentResult.failed(AgentType.CAREER_ADVISOR, "AI reasoning timed out", "TIMEOUT"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.CAREER_ADVISOR, failingAdvisor));

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        // Career advisor failed but is non-blocking - orchestration should complete
        assertEquals(AgentStatus.FAILED, run.resultOf(AgentType.CAREER_ADVISOR).status());
        assertEquals("TIMEOUT", run.resultOf(AgentType.CAREER_ADVISOR).errorCode());
        // APPLICATION_ADVISOR should still run
        assertEquals(AgentStatus.COMPLETED, run.resultOf(AgentType.APPLICATION_ADVISOR).status());
        // Run status should be PARTIAL (optional agent failed)
        assertEquals(RunStatus.PARTIAL, run.runStatus());
        assertFalse(run.success());
    }

    @Test
    @DisplayName("orchestration run contains AI and tool call counts")
    void orchestrationRunContainsCallCounts() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertTrue(run.aiCallsUsed() >= 0);
        assertTrue(run.toolCallsUsed() >= 0);
        assertNotNull(run.message());
    }

    @Test
    @DisplayName("failed blocking agent sets stoppingAgentType in OrchestrationRun")
    void blockingFailureSetsStoppingAgentType() {
        List<AgentType> log = new ArrayList<>();
        CareerAgent failingResume = realistic(AgentType.RESUME, log,
                (r, c) -> AgentResult.failed(AgentType.RESUME, "boom", "RESUME_FAILED"));
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(
                withReplaced(suite(log), AgentType.RESUME, failingResume));

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("some resume text that is long enough to parse");

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        assertEquals(RunStatus.FAILED, run.runStatus());
        assertEquals(AgentType.RESUME, run.stoppingAgentType());
        assertEquals(AgentType.RESUME, run.resultOf(AgentType.RESUME).agentType());
        assertEquals("RESUME_FAILED", run.resultOf(AgentType.RESUME).errorCode());
    }

    @Test
    @DisplayName("agent results have NONE error code when successful")
    void successfulAgentResultsHaveNoneErrorCode() {
        List<AgentType> log = new ArrayList<>();
        CareerAgentOrchestrator orc = new CareerAgentOrchestrator(suite(log));
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        OrchestrationRun run = orc.orchestrateTracked(ctx);

        for (AgentType type : AgentType.values()) {
            AgentResult result = run.resultOf(type);
            if (result.status() == AgentStatus.COMPLETED) {
                assertEquals("NONE", result.errorCode(), "Error code should be NONE for " + type);
            }
        }
    }
}
