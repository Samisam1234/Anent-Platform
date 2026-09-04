package com.agentplatform.orchestrator.agent;

/**
 * Structured input handed to an agent for execution.
 *
 * <p>Typed and minimal: the bulk of shared state travels in the mutable
 * {@link AgentContext}; the request carries only the per-invocation action
 * parameters an agent needs. Avoids Object-based APIs.</p>
 */
public record AgentRequest(
        AgentType agentType
) {

    /** Compact constructor guarding against a null agent type. */
    public AgentRequest {
        if (agentType == null) {
            throw new IllegalArgumentException("agentType must not be null");
        }
    }

    public static AgentRequest of(AgentType agentType) {
        return new AgentRequest(agentType);
    }
}
