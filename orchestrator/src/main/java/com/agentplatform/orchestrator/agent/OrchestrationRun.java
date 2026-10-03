package com.agentplatform.orchestrator.agent;

import java.time.Duration;
import java.time.Instant;
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
 *
 * <p>Execution timeline: {@link #executionTimeline()} exposes a focused,
 * PII-free ordered list of {@link AgentExecutionEvent}s suitable for
 * structured logging and API exposure. {@link #toExecutionLog()} formats this
 * into a single multi-line string for SLF4J output.</p>
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

    // ─── Timeline ────────────────────────────────────────────────────────────

    /**
     * Lightweight, PII-free timeline entry for a single agent within this run.
     * Carries only timing and status — no message, no output, no prompt content.
     */
    public record AgentExecutionEvent(
            AgentType agentType,
            AgentStatus status,
            long durationMs,
            Instant startedAt,
            Instant completedAt,
            String errorCode
    ) {
    }

    /**
     * Returns the ordered execution timeline: one {@link AgentExecutionEvent}
     * per agent in the fixed pipeline order. The list is immutable.
     * Timeline events never carry messages, output payloads, or PII.
     */
    public List<AgentExecutionEvent> executionTimeline() {
        List<AgentExecutionEvent> events = new ArrayList<>(agentExecutions.size());
        for (AgentResult r : agentExecutions) {
            events.add(new AgentExecutionEvent(
                    r.agentType(), r.status(), r.durationMs(),
                    r.startedAt(), r.completedAt(), r.errorCode()));
        }
        return List.copyOf(events);
    }

    /**
     * Wall-clock duration of the entire run: from the earliest {@code startedAt}
     * to the latest {@code completedAt} across all agents. Returns {@code -1}
     * when no timing data is available.
     */
    public long totalDurationMs() {
        Instant earliest = null;
        Instant latest = null;
        for (AgentResult r : agentExecutions) {
            if (r.startedAt() != null && (earliest == null || r.startedAt().isBefore(earliest))) {
                earliest = r.startedAt();
            }
            if (r.completedAt() != null && (latest == null || r.completedAt().isAfter(latest))) {
                latest = r.completedAt();
            }
        }
        if (earliest == null || latest == null) {
            return -1;
        }
        return Duration.between(earliest, latest).toMillis();
    }

    /**
     * Formats the execution timeline as a structured multi-line string suitable
     * for a single SLF4J log entry. Contains no PII — only agent type, status,
     * duration, error code, and the run status.
     */
    public String toExecutionLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("EXECUTION_TIMELINE runStatus=").append(runStatus);
        sb.append(" totalDurationMs=").append(totalDurationMs());
        sb.append(" aiCallsUsed=").append(aiCallsUsed);
        sb.append(" toolCallsUsed=").append(toolCallsUsed);
        sb.append('\n');
        int idx = 1;
        for (AgentExecutionEvent e : executionTimeline()) {
            sb.append("  #").append(idx++).append(' ');
            sb.append(e.agentType()).append(' ');
            sb.append(e.status()).append(' ');
            sb.append(e.durationMs()).append("ms ");
            sb.append(e.errorCode());
            sb.append('\n');
        }
        return sb.toString();
    }

    // ─── Existing helpers ────────────────────────────────────────────────────

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
