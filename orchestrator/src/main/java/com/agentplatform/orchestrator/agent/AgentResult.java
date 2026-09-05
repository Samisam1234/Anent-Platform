package com.agentplatform.orchestrator.agent;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured result produced by an agent.
 *
 * <p>Contains at minimum the agent type, status, success flag, a safe
 * human-readable message, an optional structured output reference, and an
 * optional error code. Raw exception objects and stack traces are never
 * exposed — {@link #message()} carries only safe, user-facing text.</p>
 *
 * <p>Execution timing ({@link #startedAt()}, {@link #completedAt()},
 * {@link #durationMs()}) is captured for observability and UI progress display.
 * These fields are populated by the orchestrator when the agent runs.</p>
 */
public record AgentResult(
        AgentType agentType,
        AgentStatus status,
        boolean success,
        String message,
        Object output,
        String errorCode,
        Instant startedAt,
        Instant completedAt
) {

    public AgentResult {
        if (agentType == null) {
            throw new IllegalArgumentException("agentType must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        message = message == null ? "" : message;
        errorCode = errorCode == null ? "NONE" : errorCode;
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public static AgentResult completed(AgentType type, String message, Object output) {
        return new AgentResult(type, AgentStatus.COMPLETED, true, message, output, null, null, null);
    }

    public static AgentResult completed(AgentType type, String message) {
        return new AgentResult(type, AgentStatus.COMPLETED, true, message, null, null, null, null);
    }

    public static AgentResult failed(AgentType type, String message, String errorCode) {
        return new AgentResult(type, AgentStatus.FAILED, false, message, null, errorCode, null, null);
    }

    public static AgentResult skipped(AgentType type, String message) {
        return new AgentResult(type, AgentStatus.SKIPPED, false, message, null, null, null, null);
    }

    /** Returns the output cast to the expected structured type, or null. */
    @SuppressWarnings("unchecked")
    public <T> T outputAs(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        if (output == null || !type.isAssignableFrom(output.getClass())) {
            return null;
        }
        return (T) output;
    }

    /**
     * Returns the execution duration in milliseconds, or -1 if not available.
     */
    public long durationMs() {
        if (startedAt == null || completedAt == null) {
            return -1L;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /**
     * Creates a copy of this result with the completed timestamp set to now.
     * Used by the orchestrator when an agent finishes.
     */
    public AgentResult withCompletionTime() {
        return new AgentResult(agentType, status, success, message, output, errorCode, startedAt, Instant.now());
    }

    /**
     * Creates a copy of this result with the started timestamp set to now.
     * Used by the orchestrator when an agent starts.
     */
    public AgentResult withStartTime() {
        return new AgentResult(agentType, status, success, message, output, errorCode, Instant.now(), completedAt);
    }
}
