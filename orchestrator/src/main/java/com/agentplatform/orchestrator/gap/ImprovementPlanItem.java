package com.agentplatform.orchestrator.gap;

import java.util.List;

/**
 * One item of an {@link CareerImprovementPlan}.
 *
 * <p>The structured fields {@code rank}/{@code type}/{@code focus}/{@code reason} are
 * authoritative and always come from the deterministic {@link ImprovementPriority}
 * layer — the LLM never controls them. {@code explanation} and
 * {@code suggestedActions} are advisory prose produced either by the LLM (after a
 * successful hallucination-guard validation) or by the deterministic fallback.</p>
 */
public record ImprovementPlanItem(
        int rank,
        String focus,
        String type,
        String reason,
        String explanation,
        List<String> suggestedActions
) {
    public ImprovementPlanItem {
        focus = focus == null ? "" : focus.trim();
        type = type == null ? "" : type.trim();
        reason = reason == null ? "" : reason.trim();
        explanation = explanation == null ? "" : explanation.trim();
        suggestedActions = suggestedActions != null ? List.copyOf(suggestedActions) : List.of();
    }
}