package com.agentplatform.orchestrator.gap;

import java.util.List;

/**
 * An AI-assisted career improvement plan built on the deterministic outputs of
 * {@link CareerGapAnalysisService} (Steps 4.1–4.2).
 *
 * <p>The deterministic layer is authoritative. The LLM may only explain and organize
 * the supplied priorities — it cannot add skills, change ordering/types, remove a
 * required priority or invent experience. Its {@code origin} records how the plan
 * was produced: {@code "AI"} when a validated model response was used, otherwise
 * {@code "DETERMINISTIC"}.</p>
 */
public record CareerImprovementPlan(
        String jobId,
        Long candidateId,
        String summary,
        String origin,
        List<ImprovementPlanItem> priorityItems
) {
    public static final String ORIGIN_AI = "AI";
    public static final String ORIGIN_DETERMINISTIC = "DETERMINISTIC";

    public CareerImprovementPlan {
        summary = summary == null ? "" : summary.trim();
        origin = origin == null ? ORIGIN_DETERMINISTIC : origin;
        priorityItems = priorityItems != null ? List.copyOf(priorityItems) : List.of();
    }
}