package com.agentplatform.orchestrator.agent;

/**
 * A single AI-provided (or deterministically-fallback) explanation for one
 * authoritative topic within an agent's reasoning output.
 */
public record Explanation(
        String topic,
        String explanation
) {
    public Explanation {
        topic = topic == null ? "" : topic.trim();
        explanation = explanation == null ? "" : explanation.trim();
    }
}
