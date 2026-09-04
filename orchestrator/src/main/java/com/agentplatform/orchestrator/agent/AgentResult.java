package com.agentplatform.orchestrator.agent;

import java.util.Objects;

/**
 * Structured result produced by an agent.
 *
 * <p>Contains at minimum the agent type, status, success flag, a safe
 * human-readable message, an optional structured output reference, and an
 * optional error code. Raw exception objects and stack traces are never
 * exposed — {@link #message()} carries only safe, user-facing text.</p>
 */
public record AgentResult(
        AgentType agentType,
        AgentStatus status,
        boolean success,
        String message,
        Object output,
        String errorCode
) {

    public AgentResult {
        if (agentType == null) {
            throw new IllegalArgumentException("agentType must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        message = message == null ? "" : message;
    }

    public static AgentResult completed(AgentType type, String message, Object output) {
        return new AgentResult(type, AgentStatus.COMPLETED, true, message, output, null);
    }

    public static AgentResult completed(AgentType type, String message) {
        return new AgentResult(type, AgentStatus.COMPLETED, true, message, null, null);
    }

    public static AgentResult failed(AgentType type, String message, String errorCode) {
        return new AgentResult(type, AgentStatus.FAILED, false, message, null, errorCode);
    }

    public static AgentResult skipped(AgentType type, String message) {
        return new AgentResult(type, AgentStatus.SKIPPED, false, message, null, null);
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
}
