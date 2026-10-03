package com.agentplatform.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Phase 9 execution timeline and structured logging
 * on {@link OrchestrationRun}.
 *
 * <p>Hermetic: no Spring, no DB, no LLM. Directly constructs
 * {@link AgentResult} records and {@link OrchestrationRun} instances.</p>
 */
@DisplayName("OrchestrationRun — execution timeline & structured log (Phase 9)")
class OrchestrationRunTimelineTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private AgentResult completed(AgentType type, Instant start, Instant end) {
        AgentResult r = AgentResult.completed(type, "done");
        return new AgentResult(type, AgentStatus.COMPLETED, true, "done", null,
                "NONE", start, end);
    }

    private AgentResult failed(AgentType type, String code) {
        AgentResult r = AgentResult.failed(type, "boom", code);
        return new AgentResult(type, AgentStatus.FAILED, false, "boom", null,
                code, T0, T0);
    }

    private AgentResult skipped(AgentType type, String reason) {
        AgentResult r = AgentResult.skipped(type, reason);
        return new AgentResult(type, AgentStatus.SKIPPED, false, reason, null,
                "NONE", T0, T0);
    }

    // ─── executionTimeline ───────────────────────────────────────────────────

    @Test
    @DisplayName("executionTimeline returns one event per agent in order")
    void timelineSizeAndOrder() {
        List<AgentResult> results = List.of(
                completed(AgentType.RESUME, T0, T0.plusMillis(10)),
                completed(AgentType.JOB_DISCOVERY, T0.plusMillis(10), T0.plusMillis(20)),
                skipped(AgentType.MATCHING, "no data"),
                skipped(AgentType.CAREER_ADVISOR, "no data"),
                skipped(AgentType.APPLICATION_ADVISOR, "no data")
        );
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.PARTIAL, results, 0, 0, false, "partial", null);

        List<OrchestrationRun.AgentExecutionEvent> timeline = run.executionTimeline();

        assertEquals(5, timeline.size());
        assertEquals(AgentType.RESUME, timeline.get(0).agentType());
        assertEquals(AgentType.JOB_DISCOVERY, timeline.get(1).agentType());
        assertEquals(AgentType.MATCHING, timeline.get(2).agentType());
        assertEquals(AgentType.CAREER_ADVISOR, timeline.get(3).agentType());
        assertEquals(AgentType.APPLICATION_ADVISOR, timeline.get(4).agentType());
    }

    @Test
    @DisplayName("timeline events carry status, duration, error code — no message/output")
    void timelineEventFields() {
        AgentResult res = completed(AgentType.RESUME, T0, T0.plusMillis(42));
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.COMPLETED, List.of(res), 0, 0, true, "ok", null);

        OrchestrationRun.AgentExecutionEvent event = run.executionTimeline().get(0);

        assertEquals(AgentType.RESUME, event.agentType());
        assertEquals(AgentStatus.COMPLETED, event.status());
        assertEquals(42, event.durationMs());
        assertEquals(T0, event.startedAt());
        assertEquals(T0.plusMillis(42), event.completedAt());
        assertEquals("NONE", event.errorCode());
    }

    @Test
    @DisplayName("timeline is immutable")
    void timelineImmutable() {
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.COMPLETED, List.of(
                completed(AgentType.RESUME, T0, T0.plusMillis(1))),
                0, 0, true, "ok", null);

        assertThrows(UnsupportedOperationException.class,
                () -> run.executionTimeline().add(
                        new OrchestrationRun.AgentExecutionEvent(
                                AgentType.MATCHING, AgentStatus.SKIPPED, 0,
                                T0, T0, "NONE")));
    }

    // ─── totalDurationMs ─────────────────────────────────────────────────────

    @Test
    @DisplayName("totalDurationMs spans earliest start to latest completion")
    void totalDurationComputesCorrectly() {
        List<AgentResult> results = List.of(
                completed(AgentType.RESUME, T0.plusMillis(100), T0.plusMillis(200)),
                completed(AgentType.JOB_DISCOVERY, T0.plusMillis(250), T0.plusMillis(400))
        );
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.COMPLETED, results, 0, 0, true, "ok", null);

        // Earliest start: T0+100, latest completion: T0+400 → 300ms
        assertEquals(300, run.totalDurationMs());
    }

    @Test
    @DisplayName("totalDurationMs returns -1 when no timing data")
    void totalDurationNoTiming() {
        AgentResult noTiming = new AgentResult(
                AgentType.RESUME, AgentStatus.SKIPPED, false, "skip", null,
                "NONE", null, null);
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.PARTIAL, List.of(noTiming), 0, 0, false, "partial", null);

        assertEquals(-1, run.totalDurationMs());
    }

    @Test
    @DisplayName("totalDurationMs returns -1 for empty execution list")
    void totalDurationEmpty() {
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.PARTIAL, List.of(), 0, 0, false, "empty", null);

        assertEquals(-1, run.totalDurationMs());
    }

    // ─── toExecutionLog ──────────────────────────────────────────────────────

    @Test
    @DisplayName("toExecutionLog contains run status and per-agent lines")
    void executionLogStructure() {
        List<AgentResult> results = List.of(
                completed(AgentType.RESUME, T0, T0.plusMillis(10)),
                skipped(AgentType.MATCHING, "no data")
        );
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.PARTIAL, results, 1, 2, false, "partial", null);

        String log = run.toExecutionLog();

        assertTrue(log.contains("EXECUTION_TIMELINE"));
        assertTrue(log.contains("runStatus=PARTIAL"));
        assertTrue(log.contains("aiCallsUsed=1"));
        assertTrue(log.contains("toolCallsUsed=2"));
        assertTrue(log.contains("RESUME"));
        assertTrue(log.contains("COMPLETED"));
        assertTrue(log.contains("MATCHING"));
        assertTrue(log.contains("SKIPPED"));
        assertTrue(log.contains("#1"));
        assertTrue(log.contains("#2"));
    }

    @Test
    @DisplayName("toExecutionLog does not contain PII or prompts")
    void executionLogNoPii() {
        AgentResult res = new AgentResult(
                AgentType.RESUME, AgentStatus.COMPLETED, true,
                "My secret resume text with SSN 123-45-6789", null,
                "NONE", T0, T0.plusMillis(5));
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.COMPLETED, List.of(res), 0, 0, true, "ok", null);

        String log = run.toExecutionLog();

        assertFalse(log.contains("secret"), "execution log must not expose message content");
        assertFalse(log.contains("SSN"), "execution log must not expose PII");
        // The log SHOULD contain the agent type and status
        assertTrue(log.contains("RESUME"));
        assertTrue(log.contains("COMPLETED"));
        assertTrue(log.contains("5ms"));
    }

    // ─── Full run integration with timeline ──────────────────────────────────

    @Test
    @DisplayName("full 5-agent run produces complete timeline")
    void fullRunTimeline() {
        List<AgentResult> results = List.of(
                completed(AgentType.RESUME, T0, T0.plusMillis(10)),
                completed(AgentType.JOB_DISCOVERY, T0.plusMillis(10), T0.plusMillis(20)),
                completed(AgentType.MATCHING, T0.plusMillis(20), T0.plusMillis(30)),
                completed(AgentType.CAREER_ADVISOR, T0.plusMillis(30), T0.plusMillis(40)),
                completed(AgentType.APPLICATION_ADVISOR, T0.plusMillis(40), T0.plusMillis(50))
        );
        OrchestrationRun run = new OrchestrationRun(
                RunStatus.COMPLETED, results, 0, 0, true, "ok", null);

        assertEquals(5, run.executionTimeline().size());
        assertEquals(50, run.totalDurationMs());
        for (OrchestrationRun.AgentExecutionEvent e : run.executionTimeline()) {
            assertEquals(AgentStatus.COMPLETED, e.status());
            assertEquals("NONE", e.errorCode());
        }
    }
}
