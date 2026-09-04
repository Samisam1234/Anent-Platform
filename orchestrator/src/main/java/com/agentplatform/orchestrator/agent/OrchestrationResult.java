package com.agentplatform.orchestrator.agent;

import java.util.List;

/**
 * Aggregate result of a single {@link CareerAgentOrchestrator} run.
 *
 * <p>Carries the overall outcome: the agent type that stopped the pipeline (if a
 * blocking failure aborted it), the final status, whether the run was fully
 * successful, a safe message, and the ordered list of individually executed agent
 * results. Optional/skipped stages are permitted without making the run "failed";
 * {@link #success()} only reflects whether every stage that was required to run
 * completed.</p>
 */
public record OrchestrationResult(
        AgentType stoppingAgentType,
        AgentStatus status,
        boolean success,
        String message,
        List<AgentResult> executed,
        boolean blockingFailure
) {

    public OrchestrationResult {
        message = message == null ? "" : message;
        executed = executed != null ? List.copyOf(executed) : List.of();
    }
}
