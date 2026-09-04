package com.agentplatform.orchestrator.agent;

/**
 * Common abstraction for a logical career agent.
 *
 * <p>An agent identifies itself, exposes its required precondition, and executes
 * against a structured {@link AgentRequest} and shared {@link AgentContext},
 * returning a structured {@link AgentResult}. Implementations are synchronous
 * and bounded — they never spawn background work, never recurse, and never
 * invoke other agents directly (the {@code CareerAgentOrchestrator} owns any
 * sequencing).</p>
 *
 * <p>Implementations delegate to existing deterministic services; they do not
 * re-implement matching, parsing, or scoring logic.</p>
 */
public interface CareerAgent {

    /** Returns the logical type of this agent. */
    AgentType type();

    /**
     * Whether this agent can run given the current context. Agents that depend
     * on prior stages return false when their required input is absent, letting
     * the orchestrator skip them safely.
     */
    boolean canExecute(AgentContext context);

    /**
     * Executes this agent. Should return a completed or failed {@link AgentResult}
     * and never throw — the orchestrator treats thrown exceptions as failures.
     */
    AgentResult execute(AgentRequest request, AgentContext context);
}
