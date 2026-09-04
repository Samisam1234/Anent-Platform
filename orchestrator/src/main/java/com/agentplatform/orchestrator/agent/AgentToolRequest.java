package com.agentplatform.orchestrator.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A controlled, typed request from a logical agent to perform a permitted tool
 * operation.
 *
 * <p>Arguments are carried as a {@code Map<String, Object>} (a stable, JSON-friendly
 * shape) rather than raw {@code Object} scaffolding, and are defensively copied on
 * construction so callers cannot mutate a constructed request. The structured
 * {@code agentType} + {@code toolName} pair lets the tool orchestrator apply the
 * {@link AgentToolPolicy} before anything executes.</p>
 */
public record AgentToolRequest(
        AgentType agentType,
        String toolName,
        Map<String, Object> arguments,
        String reason
) {

    /** Upper bound on any single argument value, to keep prompts/execution bounded. */
    public static final int MAX_ARGUMENT_LENGTH = 4000;

    /** Upper bound on the number of arguments in a request. */
    public static final int MAX_ARGUMENT_COUNT = 16;

    public AgentToolRequest {
        if (agentType == null) {
            throw new IllegalArgumentException("agentType must not be null");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        toolName = toolName.trim();
        arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        reason = reason == null ? "" : reason.trim();
    }

    /**
     * Convenience constructor without a reason.
     */
    public AgentToolRequest(AgentType agentType, String toolName, Map<String, Object> arguments) {
        this(agentType, toolName, arguments, "");
    }

    /**
     * Validates that the request shape is acceptable for orchestration: tool and
     * agent present (already enforced by the constructor), bounded argument count,
     * and no oversized argument values. Returns {@code null} when valid, otherwise
     * a structured error code for the caller to surface safely.
     */
    public String validationError() {
        if (agentType == null || toolName == null || toolName.isBlank()) {
            return "INVALID_REQUEST";
        }
        if (arguments != null && arguments.size() > MAX_ARGUMENT_COUNT) {
            return "TOO_MANY_ARGUMENTS";
        }
        if (arguments != null) {
            for (Map.Entry<String, Object> e : arguments.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    return "INVALID_ARGUMENT_NAME";
                }
                if (e.getKey().length() > 200) {
                    return "INVALID_ARGUMENT_NAME";
                }
                if (e.getValue() != null && e.getValue().toString().length() > MAX_ARGUMENT_LENGTH) {
                    return "ARGUMENT_TOO_LARGE";
                }
            }
        }
        return null;
    }

    /**
     * A normalized request whose argument map is guaranteed to be a mutable-safe,
     * bounded copy usable for execution.
     */
    public Map<String, Object> safeArguments() {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (arguments != null) {
            arguments.forEach((k, v) -> copy.put(k, v));
        }
        return Map.copyOf(copy);
    }
}
