package com.agentplatform.orchestrator.advisor;

import java.util.List;

/**
 * Structured detail for a recommended application action.
 *
 * <p>Mirrors the structured fields of {@link com.agentplatform.orchestrator.gap.ImprovementPriority}
 * to provide programmatic access to rank, focus, type, reason, and description
 * while keeping the existing {@code List<String> recommendedActions} for
 * backward compatibility.</p>
 *
 * <p>Both {@code recommendedActions} and {@code recommendedActionDetails} are
 * derived from the same {@link com.agentplatform.orchestrator.gap.ImprovementPriority}
 * list in identical rank order, and {@code description} values are identical.</p>
 */
public record RecommendedActionDetail(
        int rank,
        String focus,
        String type,
        String reason,
        String description
) {

    public RecommendedActionDetail {
        focus = focus == null ? "" : focus.trim();
        type = type == null ? "" : type.trim();
        reason = reason == null ? "" : reason.trim();
        description = description == null ? "" : description.trim();
    }

    /**
     * Creates a detail from an {@link com.agentplatform.orchestrator.gap.ImprovementPriority}.
     */
    public static RecommendedActionDetail fromImprovementPriority(
            com.agentplatform.orchestrator.gap.ImprovementPriority priority) {
        return new RecommendedActionDetail(
                priority.rank(),
                priority.focus(),
                priority.type(),
                priority.reason(),
                priority.description()
        );
    }
}

