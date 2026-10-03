package com.agentplatform.orchestrator.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, immutable snapshot of a single orchestration run for execution
 * tracking.
 *
 * <p>Captures the overall {@link RunStatus}, the ordered final state of every
 * logical agent (all of {@link AgentType} in the fixed {@code RESUME →
 * JOB_DISCOVERY → MATCHING → CAREER_ADVISOR → APPLICATION_ADVISOR} order, each in
 * exactly one of {@code COMPLETED}/{@code FAILED}/{@code SKIPPED}), the
 * deterministic AI/tool usage counts (derived from the existing
 * {@link AgentContext} budgets), and safe failure information. It stores
 * references to existing domain results — it never mutates them. Note: this is
 * in-memory only; there is no persistence.</p>
 *
 * <p>No agent ever remains {@code RUNNING} after orchestration returns. The
 * status rules are deterministic and documented on {@link #resolveStatus}.</p>
 */
public record OrchestrationRun(
        RunStatus runStatus,
        List<AgentResult> agentExecutions,
        int aiCallsUsed,
        int toolCallsUsed,
        boolean success,
        String message,
        AgentType stoppingAgentType
) {

    public OrchestrationRun {
        agentExecutions = agentExecutions != null ? List.copyOf(agentExecutions) : List.of();
        message = message == null ? "" : message;
    }

    /** All agents that ended {@link AgentStatus#COMPLETED}. */
    public List<AgentType> completedAgents() {
        return agentsWithStatus(AgentStatus.COMPLETED);
    }

    /** All agents that ended {@link AgentStatus#FAILED}. */
    public List<AgentType> failedAgents() {
        return agentsWithStatus(AgentStatus.FAILED);
    }

    /** All agents that ended {@link AgentStatus#SKIPPED}. */
    public List<AgentType> skippedAgents() {
        return agentsWithStatus(AgentStatus.SKIPPED);
    }

    public AgentResult resultOf(AgentType type) {
        if (type == null) {
            return null;
        }
        for (AgentResult r : agentExecutions) {
            if (r.agentType() == type) {
                return r;
            }
        }
        return null;
    }

    private List<AgentType> agentsWithStatus(AgentStatus status) {
        List<AgentType> out = new ArrayList<>();
        for (AgentResult r : agentExecutions) {
            if (r.status() == status) {
                out.add(r.agentType());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Deterministically resolves the overall run status from the per-agent
     * outcomes:
     * <ol>
     *   <li>a blocking agent failed → {@link RunStatus#FAILED};</li>
     *   <li>otherwise any agent failed (optional) → {@link RunStatus#PARTIAL};</li>
     *   <li>otherwise no agent completed (everything skipped) →
     *       {@link RunStatus#PARTIAL} (nothing usable produced);</li>
     *   <li>otherwise → {@link RunStatus#COMPLETED} (skips allowed).</li>
     * </ol>
     */
    public static RunStatus resolveStatus(List<AgentResult> executions,
                                          boolean blockingFailure) {
        if (executions == null || executions.isEmpty()) {
            return RunStatus.PARTIAL;
        }
        if (blockingFailure) {
            return RunStatus.FAILED;
        }
        boolean anyFailed = false;
        boolean anyCompleted = false;
        for (AgentResult r : executions) {
            if (r.status() == AgentStatus.FAILED) {
                anyFailed = true;
            }
            if (r.status() == AgentStatus.COMPLETED) {
                anyCompleted = true;
            }
        }
        if (anyFailed) {
            return RunStatus.PARTIAL;
        }
        if (!anyCompleted) {
            return RunStatus.PARTIAL;
        }
        return RunStatus.COMPLETED;
    }
}
