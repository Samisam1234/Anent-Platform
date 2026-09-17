package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.agent.AgentResult;
import com.agentplatform.orchestrator.agent.OrchestrationRun;

import java.time.Instant;
import java.util.List;

/**
 * Safe, API-facing representation of an {@link OrchestrationRun}.
 *
 * <p>Exposes only execution status, safe per-agent summaries, and the
 * execution timeline — never resume text, phone/email, raw tool arguments,
 * credentials, exception objects, or stack traces.</p>
 *
 * @param runStatus         overall run status (PENDING/RUNNING/COMPLETED/PARTIAL/FAILED)
 * @param success           whether the run completed without failures
 * @param message           safe human-readable message
 * @param agentExecutions   ordered per-agent execution summaries (fixed agent order)
 * @param aiCallsUsed       AI calls consumed this run
 * @param toolCallsUsed     tool calls consumed this run
 * @param stoppingAgentType the blocking agent that halted the run (null if none)
 * @param totalDurationMs   wall-clock duration of the entire run in milliseconds
 * @param executionTimeline ordered timeline entries (agent, status, timing) — PII-free
 */
public record OrchestrationRunResponseDto(
        String runStatus,
        boolean success,
        String message,
        List<AgentExecutionDto> agentExecutions,
        int aiCallsUsed,
        int toolCallsUsed,
        String stoppingAgentType,
        long totalDurationMs,
        List<ExecutionTimelineEntryDto> executionTimeline
) {

    public OrchestrationRunResponseDto {
        agentExecutions = agentExecutions != null ? List.copyOf(agentExecutions) : List.of();
        executionTimeline = executionTimeline != null ? List.copyOf(executionTimeline) : List.of();
    }

    public static OrchestrationRunResponseDto from(OrchestrationRun run) {
        List<AgentExecutionDto> executions = run.agentExecutions().stream()
                .map(AgentExecutionDto::from)
                .toList();
        List<ExecutionTimelineEntryDto> timeline = run.executionTimeline().stream()
                .map(ExecutionTimelineEntryDto::from)
                .toList();
        return new OrchestrationRunResponseDto(
                run.runStatus() == null ? null : run.runStatus().name(),
                run.success(),
                run.message(),
                executions,
                run.aiCallsUsed(),
                run.toolCallsUsed(),
                run.stoppingAgentType() == null ? null : run.stoppingAgentType().name(),
                run.totalDurationMs(),
                timeline
        );
    }

    /**
     * Safe per-agent execution summary (no stack traces, no tool arguments,
     * no raw exception class names).
     */
    public record AgentExecutionDto(
            String agentType,
            String status,
            boolean success,
            String message,
            String errorCode,
            String startedAt,
            String completedAt,
            long durationMs
    ) {

        public static AgentExecutionDto from(AgentResult r) {
            Instant started = r.startedAt();
            Instant completed = r.completedAt();
            return new AgentExecutionDto(
                    r.agentType() == null ? null : r.agentType().name(),
                    r.status() == null ? null : r.status().name(),
                    r.success(),
                    r.message(),
                    r.errorCode(),
                    started == null ? null : started.toString(),
                    completed == null ? null : completed.toString(),
                    r.durationMs()
            );
        }
    }

    /**
     * PII-free timeline entry for a single agent — carries only type, status,
     * timing, and error code. No message, no output, no prompts.
     */
    public record ExecutionTimelineEntryDto(
            String agentType,
            String status,
            long durationMs,
            String startedAt,
            String completedAt,
            String errorCode
    ) {

        public static ExecutionTimelineEntryDto from(OrchestrationRun.AgentExecutionEvent e) {
            return new ExecutionTimelineEntryDto(
                    e.agentType() == null ? null : e.agentType().name(),
                    e.status() == null ? null : e.status().name(),
                    e.durationMs(),
                    e.startedAt() == null ? null : e.startedAt().toString(),
                    e.completedAt() == null ? null : e.completedAt().toString(),
                    e.errorCode()
            );
        }
    }
}
