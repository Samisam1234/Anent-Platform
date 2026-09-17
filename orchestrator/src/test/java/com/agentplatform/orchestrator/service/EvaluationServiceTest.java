package com.agentplatform.orchestrator.service;

import com.agentplatform.orchestrator.agent.AgentContext;
import com.agentplatform.orchestrator.agent.EvaluationResult;
import com.agentplatform.orchestrator.agent.OrchestrationRun;
import com.agentplatform.orchestrator.agent.RunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link EvaluationService}.
 *
 * <p>Hermetic: no Spring, no DB, no LLM. Uses the real stateless service
 * with directly constructed domain objects.</p>
 */
@DisplayName("EvaluationService — stateless evaluation extraction (Phase 10)")
class EvaluationServiceTest {

    private final EvaluationService service = new EvaluationService();

    // ─── Full evaluation ────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate delegates to EvaluationResult.from and returns populated result")
    void evaluateDelegatesToFrom() {
        var run = new OrchestrationRun(RunStatus.COMPLETED, List.of(), 2, 3, true, "ok", null);
        var context = new AgentContext();

        EvaluationResult result = service.evaluate(run, context);

        assertNotNull(result);
        assertEquals(2, result.aiCallsUsed());
        assertEquals(3, result.toolCallsUsed());
        assertTrue(result.totalDurationMs() >= -1);
    }

    @Test
    @DisplayName("evaluate with null run returns empty evaluation")
    void evaluateNullRunReturnsEmpty() {
        var result = service.evaluate(null, new AgentContext());

        assertEquals(false, result.pipelineCompleted());
        assertEquals(0, result.agentsCompleted());
        assertEquals(-1, result.totalDurationMs());
    }

    @Test
    @DisplayName("evaluate with null context returns run-derived metrics")
    void evaluateNullContextReturnsRunMetrics() {
        var run = new OrchestrationRun(RunStatus.COMPLETED, List.of(), 0, 0, true, "ok", null);
        var result = service.evaluate(run, null);

        assertEquals(true, result.pipelineCompleted());
        assertEquals(0, result.agentsCompleted());
    }

    // ─── Determinism ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("identical inputs produce identical evaluation")
    void deterministic() {
        var run = new OrchestrationRun(RunStatus.COMPLETED, List.of(), 1, 2, true, "ok", null);
        var context = new AgentContext();

        var r1 = service.evaluate(run, context);
        var r2 = service.evaluate(run, context);

        assertEquals(r1, r2);
    }

    // ─── No side effects ──────────────────────────────────────────────────────

    @Test
    @DisplayName("service has no internal state — multiple calls independent")
    void noInternalState() {
        var run1 = new OrchestrationRun(RunStatus.COMPLETED, List.of(), 1, 1, true, "a", null);
        var run2 = new OrchestrationRun(RunStatus.PARTIAL, List.of(), 2, 2, false, "b", null);

        var r1 = service.evaluate(run1, new AgentContext());
        var r2 = service.evaluate(run2, new AgentContext());

        assertEquals(1, r1.aiCallsUsed());
        assertEquals(2, r2.aiCallsUsed());
        assertTrue(r1.pipelineCompleted());
        assertTrue(r2.pipelineCompleted() == false);
    }
}