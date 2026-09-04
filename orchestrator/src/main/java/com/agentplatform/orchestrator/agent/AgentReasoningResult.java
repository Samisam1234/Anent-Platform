package com.agentplatform.orchestrator.agent;

import java.util.List;

/**
 * Controlled reasoning output for an agent, produced by {@link AgentReasoningService}.
 *
 * <p>{@code origin} is {@link #ORIGIN_AI} when a validated model response was
 * accepted, otherwise {@link #ORIGIN_DETERMINISTIC} (fallback). The explanations
 * are always derived from authoritative deterministic facts — never invented.</p>
 */
public record AgentReasoningResult(
        AgentType agentType,
        String summary,
        List<Explanation> explanations,
        String origin
) {

    public static final String ORIGIN_AI = "AI";
    public static final String ORIGIN_DETERMINISTIC = "DETERMINISTIC";

    public AgentReasoningResult {
        // agentType may be null only via defensive null-argument calls; the
        // reasoning layer treats unknown agents as a no-op deterministic result.
        summary = summary == null ? "" : summary.trim();
        explanations = explanations != null ? List.copyOf(explanations) : List.of();
        origin = origin == null ? ORIGIN_DETERMINISTIC : origin;
    }

    /** Whether this result came from the AI model (vs a deterministic fallback). */
    public boolean aiUsed() {
        return ORIGIN_AI.equals(origin);
    }
}
