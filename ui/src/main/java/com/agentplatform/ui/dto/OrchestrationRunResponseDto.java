package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.agent.AgentResult;
import com.agentplatform.orchestrator.agent.EvaluationResult;
import com.agentplatform.orchestrator.agent.OrchestrationRun;
import com.agentplatform.orchestrator.service.OrchestrationService;

import java.time.Instant;
import java.util.List;

/**
 * Safe, API-facing representation of an {@link OrchestrationRun}.
 *
 * <p>Exposes only execution status, safe per-agent summaries, the
 * execution timeline, and deterministic evaluation metrics — never resume
 * text, phone/email, raw tool arguments, credentials, exception objects,
 * or stack traces.</p>
 *
 * @param runStatus               overall run status (PENDING/RUNNING/COMPLETED/PARTIAL/FAILED)
 * @param success                 whether the run completed without failures
 * @param message                 safe human-readable message
 * @param agentExecutions         ordered per-agent execution summaries (fixed agent order)
 * @param aiCallsUsed             AI calls consumed this run
 * @param toolCallsUsed           tool calls consumed this run
 * @param stoppingAgentType       the blocking agent that halted the run (null if none)
 * @param totalDurationMs         wall-clock duration of the entire run in milliseconds
 * @param executionTimeline       ordered timeline entries (agent, status, timing) — PII-free
 * @param evaluation              deterministic evaluation metrics (null if not computed)
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
        List<ExecutionTimelineEntryDto> executionTimeline,
        EvaluationDto evaluation
) {

    public OrchestrationRunResponseDto {
        agentExecutions = agentExecutions != null ? List.copyOf(agentExecutions) : List.of();
        executionTimeline = executionTimeline != null ? List.copyOf(executionTimeline) : List.of();
    }

    /**
     * Creates a response DTO from an {@link OrchestrationRun} only (backward compatible).
     * Evaluation will be {@code null}.
     */
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
                timeline,
                null
        );
    }

    /**
     * Creates a response DTO from an {@link OrchestrationService.OrchestrationWithEvaluation}.
     * Includes deterministic evaluation metrics.
     */
    public static OrchestrationRunResponseDto from(OrchestrationService.OrchestrationWithEvaluation wrapper) {
        OrchestrationRun run = wrapper.run();
        EvaluationResult eval = wrapper.evaluation();
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
                timeline,
                eval != null ? EvaluationDto.from(eval) : null
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

    /**
     * Deterministic evaluation snapshot for the orchestration run.
     * All fields are {@code null} when the corresponding stage was skipped,
     * failed, or produced no usable output.
     */
    public record EvaluationDto(
            // Pipeline health
            boolean pipelineCompleted,
            int agentsCompleted,
            int agentsFailed,
            int agentsSkipped,

            // Resource efficiency
            int aiCallsUsed,
            int toolCallsUsed,
            long totalDurationMs,

            // Matching quality
            Integer matchScore,
            String matchRecommendation,
            Integer skillFitScore,
            Integer roleFitScore,
            Integer locationFitScore,
            Integer experienceFitScore,
            Integer trackFitScore,
            Integer educationFitScore,

            // Gap severity
            String gapSeverity,
            Boolean trackMismatch,
            Integer missingRequiredSkills,
            Integer missingPreferredSkills,
            Boolean experienceKnowable,

            // Application readiness
            String appRecommendation,
            Integer applicationReadinessScore,
            Integer atsReadinessScore,

            // Improvement plan
            String improvementPlanOrigin,
            Integer improvementItemsCount
    ) {

        public static EvaluationDto from(EvaluationResult r) {
            return new EvaluationDto(
                    r.pipelineCompleted(),
                    r.agentsCompleted(),
                    r.agentsFailed(),
                    r.agentsSkipped(),
                    r.aiCallsUsed(),
                    r.toolCallsUsed(),
                    r.totalDurationMs(),
                    r.matchScore(),
                    r.matchRecommendation(),
                    r.skillFitScore(),
                    r.roleFitScore(),
                    r.locationFitScore(),
                    r.experienceFitScore(),
                    r.trackFitScore(),
                    r.educationFitScore(),
                    r.gapSeverity(),
                    r.trackMismatch(),
                    r.missingRequiredSkills(),
                    r.missingPreferredSkills(),
                    r.experienceKnowable(),
                    r.appRecommendation(),
                    r.applicationReadinessScore(),
                    r.atsReadinessScore(),
                    r.improvementPlanOrigin(),
                    r.improvementItemsCount()
            );
        }
    }
}
