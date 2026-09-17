package com.agentplatform.orchestrator.agent;

import com.agentplatform.logging.LoggingContext;
import com.agentplatform.logging.PiiSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Bounded sequential orchestrator for the career-agent pipeline.
 *
 * <p>Runs a fixed sequence of logical agents ({@code RESUME → JOB_DISCOVERY →
 * MATCHING → CAREER_ADVISOR → APPLICATION_ADVISOR}), invoking each only when its
 * required input exists in the {@link AgentContext}. The pipeline is strictly
 * sequential, synchronous, bounded ({@link #MAX_AGENTS} at most), and contains
 * no recursion, no self-replication, no background work, and no autonomous
 * email/application sending.</p>
 *
 * <p>Blocking failures: a failure of {@code RESUME} or {@code JOB_DISCOVERY}
 * aborts the remaining pipeline (downstream stages cannot produce meaningful
 * results). Optional failures (matching, career advisor, application advisor)
 * are recorded but do not crash the orchestrator; the run still completes and
 * returns the aggregate result.</p>
 *
 * <p>Execution tracking: {@link #orchestrateTracked} exposes a full immutable
 * {@link OrchestrationRun} (run status, the ordered final state of every agent,
 * deterministic AI/tool usage counts). Every agent ends in exactly one of
 * {@code COMPLETED}/{@code FAILED}/{@code SKIPPED}; none remains {@code RUNNING}
 * after any orchestration method returns. Tracking is in-memory only — nothing is
 * persisted.</p>
 */
@Component
public class CareerAgentOrchestrator {

    /** Upper bound on the number of agents the orchestrator will execute. */
    public static final int MAX_AGENTS = 5;

    /** Per-run maximum number of AI reasoning calls (one per AI-eligible agent). */
    public static final int MAX_AI_CALLS = 3;

    private static final Logger log = LoggerFactory.getLogger(CareerAgentOrchestrator.class);

    /** Deterministic execution order. */
    private static final List<AgentType> SEQUENCE = List.of(
            AgentType.RESUME,
            AgentType.JOB_DISCOVERY,
            AgentType.MATCHING,
            AgentType.CAREER_ADVISOR,
            AgentType.APPLICATION_ADVISOR
    );

    /** Agent types whose failure is blocking (aborts the remaining pipeline). */
    private static final Set<AgentType> BLOCKING_FAILURES = Set.of(
            AgentType.RESUME,
            AgentType.JOB_DISCOVERY
    );

    private final Map<AgentType, CareerAgent> agentsByType;

    public CareerAgentOrchestrator(List<CareerAgent> agents) {
        if (agents == null || agents.isEmpty()) {
            throw new IllegalArgumentException("At least one CareerAgent must be provided");
        }
        Map<AgentType, CareerAgent> map = new LinkedHashMap<>();
        for (CareerAgent agent : agents) {
            if (agent != null) {
                map.putIfAbsent(agent.type(), agent);
            }
        }
        if (map.size() < SEQUENCE.size()) {
            throw new IllegalArgumentException(
                    "All " + SEQUENCE.size() + " agent types are required, got " + map.size());
        }
        this.agentsByType = Map.copyOf(map);
    }

    /**
     * Executes the sequential pipeline for the given context. Returns the context
     * populated with results and recorded agent results. Never throws for agent
     * failures — it returns the aggregate outcome instead.
     *
     * @return aggregate execution result describing overall success and counts.
     */
    public OrchestrationResult orchestrate(AgentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("AgentContext must not be null");
        }
        PipelineTrace trace = runPipeline(context);
        if (trace.blockingFailure()) {
            return new OrchestrationResult(trace.stoppingAgentType(), AgentStatus.FAILED,
                    false, trace.blockingMessage(), trace.executed(), true);
        }
        boolean allSuccess = trace.allSuccess();
        return new OrchestrationResult(null, AgentStatus.COMPLETED, allSuccess,
                allSuccess ? "Career orchestration completed successfully."
                        : "Career orchestration completed with some skipped/failed stages.",
                trace.executed(), false);
    }

    /**
     * Executes the sequential pipeline and returns a full immutable execution
     * snapshot ({@link OrchestrationRun}) for tracking. Like {@link #orchestrate}
     * it never throws for agent failures. In-memory only — nothing is persisted.
     */
    public OrchestrationRun orchestrateTracked(AgentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("AgentContext must not be null");
        }
        PipelineTrace trace = runPipeline(context);

        int aiUsed = Math.max(0, MAX_AI_CALLS - context.aiCallsRemaining());
        int toolUsed = Math.max(0, AgentToolOrchestrator.MAX_TOOL_CALLS_PER_ORCHESTRATION
                - context.toolCallsRemaining());

        RunStatus status = resolveRunStatus(trace);
        String message = runMessage(status, trace.stoppingAgentType());

        OrchestrationRun run = new OrchestrationRun(status, trace.allStates(),
                aiUsed, toolUsed, status == RunStatus.COMPLETED, message,
                trace.stoppingAgentType());
        log.info("{}", run.toExecutionLog());
        return run;
    }

    // ─── Execution tracking helpers ──────────────────────────────────────────

    /**
     * Runs the fixed sequential pipeline and records the final state of every
     * agent. The returned {@link PipelineTrace} preserves the exact 6.1
     * {@code executed} semantics while also providing the full ordered state list.
     */
    private PipelineTrace runPipeline(AgentContext context) {
        LoggingContext.setRunId(UUID.randomUUID().toString());
        long runStartNanos = System.nanoTime();
        log.info("Orchestration RUN START: runId={}", LoggingContext.getRunId());
        try {
            return doRunPipeline(context);
        } finally {
            log.info("Orchestration RUN COMPLETE: totalDurationMs={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - runStartNanos));
            LoggingContext.clear();
        }
    }

    private PipelineTrace doRunPipeline(AgentContext context) {
        List<AgentResult> executed = new ArrayList<>();
        Map<AgentType, AgentResult> states = new LinkedHashMap<>();
        AgentType stoppingAgentType = null;
        boolean blockingFailure = false;
        String blockingMessage = null;

        for (AgentType type : SEQUENCE) {
            CareerAgent agent = agentsByType.get(type);
            if (agent == null) {
                continue;
            }
            if (!agent.canExecute(context)) {
                AgentResult skipped = AgentResult.skipped(type, skipReason(type));
                skipped = skipped.withStartTime().withCompletionTime();
                context.record(skipped);
                states.put(type, skipped);
                log.info("Agent SKIPPED: type={}, reason={}", type, skipReason(type));
                continue;
            }

            AgentRequest request = AgentRequest.of(type);
            // Record start time
            AgentResult running = AgentResult.completed(type, "Running");
            running = running.withStartTime();
            context.record(running);

            log.info("Agent START: type={}", type);
            AgentResult result;
            try {
                result = agent.execute(request, context);
            } catch (Exception e) {
                log.warn("Agent {} threw during execution: {}", type, PiiSanitizer.sanitize(e.getMessage()));
                result = AgentResult.failed(type, "Agent execution failed.", "AGENT_EXECUTION_ERROR");
            }
            // Record completion time
            result = result.withCompletionTime();
            context.record(result);
            states.put(type, result);

            if (!result.success()) {
                log.warn("Agent FAILED: type={}, durationMs={}, errorCode={}, blocking={}",
                        type, result.durationMs(), result.errorCode(), BLOCKING_FAILURES.contains(type));
                if (BLOCKING_FAILURES.contains(type)) {
                    log.warn("Blocking agent {} failed; aborting pipeline", type);
                    stoppingAgentType = type;
                    blockingFailure = true;
                    blockingMessage = result.message();
                    break;
                }
                log.warn("Non-blocking agent {} failed; continuing", type);
            } else {
                log.info("Agent COMPLETE: type={}, durationMs={}", type, result.durationMs());
            }
            executed.add(result);

            if (executed.size() >= MAX_AGENTS) {
                log.info("Reached maximum agent count ({}); stopping pipeline", MAX_AGENTS);
                break;
            }
        }

        // Full ordered states for tracking: fill skipped dependents after a
        // blocking failure (never fabricating results).
        List<AgentResult> allStates = new ArrayList<>();
        for (AgentType t : SEQUENCE) {
            AgentResult existing = states.get(t);
            if (existing != null) {
                allStates.add(existing);
            } else if (blockingFailure) {
                AgentResult skipped = AgentResult.skipped(t,
                        "Skipped: " + skipReason(t) + " (blocking dependency "
                                + blockingName(stoppingAgentType) + " failed).");
                skipped = skipped.withStartTime().withCompletionTime();
                allStates.add(skipped);
            }
        }

        boolean allSuccess = true;
        for (AgentResult r : context.agentResults().values()) {
            if (r.status() != AgentStatus.COMPLETED) {
                allSuccess = false;
                break;
            }
        }
        return new PipelineTrace(executed, allStates, stoppingAgentType,
                blockingFailure, blockingMessage, allSuccess);
    }

    private static RunStatus resolveRunStatus(PipelineTrace trace) {
        return OrchestrationRun.resolveStatus(trace.allStates(), trace.blockingFailure());
    }

    private static String runMessage(RunStatus status, AgentType stoppingAgentType) {
        return switch (status) {
            case FAILED -> "Blocking agent " + blockingName(stoppingAgentType)
                    + " failed; remaining dependent agents were skipped.";
            case PARTIAL -> stoppingAgentType == null
                    ? "Career orchestration completed with some stages skipped or failing."
                    : "Career orchestration completed with some stages skipped or failing.";
            default -> "Career orchestration completed successfully.";
        };
    }

    private static String blockingName(AgentType type) {
        return type == null ? "UNKNOWN" : type.name();
    }

    /** Deterministic, safe skip reason for each agent (never a stack trace). */
    private static String skipReason(AgentType type) {
        if (type == null) {
            return "Required input is not available.";
        }
        return switch (type) {
            case RESUME -> "No resume input available.";
            case JOB_DISCOVERY -> "No job discovery context available.";
            case MATCHING -> "No candidate profile or job available.";
            case CAREER_ADVISOR -> "No candidate profile/job available.";
            case APPLICATION_ADVISOR -> "No valid job/application context available.";
        };
    }

    /** Immutable result of running the pipeline, shared by both public methods. */
    private record PipelineTrace(
            List<AgentResult> executed,
            List<AgentResult> allStates,
            AgentType stoppingAgentType,
            boolean blockingFailure,
            String blockingMessage,
            boolean allSuccess
    ) {
    }
}
