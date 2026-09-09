package com.agentplatform.orchestrator.advisor;

import java.util.List;

/**
 * Structured response from the Application Advisor foundation service.
 *
 * <p>Exposes only the deterministic recommendation, readiness score, and
 * explanatory lists — never raw LLM output, tool arguments, resume text,
 * credentials, stack traces, or internal {@code AgentContext}.</p>
 *
 * <p>All collections are defensively copied to immutable lists. Strings are
 * bounded by the project's established conventions. Null collections safely
 * become empty lists.</p>
 */
public record ApplicationAdvisorResponse(
        ApplicationRecommendation recommendation,
        int applicationReadinessScore,
        List<String> strengths,
        List<String> concerns,
        List<String> recommendedActions,
        List<RecommendedActionDetail> recommendedActionDetails,
        int jobMatchScore,
        /** Title of the job this advice refers to (safe, already public job data). */
        String jobTitle,
        /** Company of the job this advice refers to (safe, already public job data). */
        String company
) {

    /**
     * Minimum valid readiness score (inclusive).
     */
    public static final int MIN_SCORE = 0;

    /**
     * Maximum valid readiness score (inclusive).
     */
    public static final int MAX_SCORE = 100;

    public ApplicationAdvisorResponse {
        recommendation = recommendation == null ? ApplicationRecommendation.NOT_RECOMMENDED : recommendation;
        applicationReadinessScore = clampScore(applicationReadinessScore);
        strengths = strengths != null ? List.copyOf(strengths) : List.of();
        concerns = concerns != null ? List.copyOf(concerns) : List.of();
        recommendedActions = recommendedActions != null ? List.copyOf(recommendedActions) : List.of();
        recommendedActionDetails = recommendedActionDetails != null ? List.copyOf(recommendedActionDetails) : List.of();
        jobMatchScore = Math.max(0, Math.min(100, jobMatchScore));
    }

    /**
     * Backwards-compatible 7-arg constructor (pre-dates the {@code jobTitle}/{@code company}
     * echo fields). Keeps every existing caller and test compiling unchanged; the two job
     * echo fields are left {@code null} and the UI renders its own placeholder.
     */
    public ApplicationAdvisorResponse(
            ApplicationRecommendation recommendation,
            int score,
            List<String> strengths,
            List<String> concerns,
            List<String> recommendedActions,
            List<RecommendedActionDetail> recommendedActionDetails,
            int jobMatchScore) {
        this(recommendation, score, strengths, concerns, recommendedActions,
                recommendedActionDetails, jobMatchScore, null, null);
    }

    private static int clampScore(int score) {
        if (score < MIN_SCORE) {
            return MIN_SCORE;
        }
        if (score > MAX_SCORE) {
            return MAX_SCORE;
        }
        return score;
    }

    /**
     * Factory for a minimal deterministic response (used by foundation service).
     */
    public static ApplicationAdvisorResponse foundation(ApplicationRecommendation recommendation, int score) {
        return new ApplicationAdvisorResponse(
                recommendation,
                score,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0);
    }

    /**
     * Factory for a fully populated response including the job echo fields
     * ({@code jobTitle} / {@code company}) the review UI displays in its header.
     */
    public static ApplicationAdvisorResponse of(
            ApplicationRecommendation recommendation,
            int score,
            List<String> strengths,
            List<String> concerns,
            List<String> recommendedActions,
            List<RecommendedActionDetail> recommendedActionDetails,
            int jobMatchScore,
            String jobTitle,
            String company) {
        return new ApplicationAdvisorResponse(
                recommendation, score, strengths, concerns, recommendedActions,
                recommendedActionDetails, jobMatchScore, jobTitle, company);
    }

    /**
     * Factory for a fully populated response.
     */
    public static ApplicationAdvisorResponse of(
            ApplicationRecommendation recommendation,
            int score,
            List<String> strengths,
            List<String> concerns,
            List<String> recommendedActions,
            List<RecommendedActionDetail> recommendedActionDetails,
            int jobMatchScore) {
        return new ApplicationAdvisorResponse(
                recommendation, score, strengths, concerns, recommendedActions, recommendedActionDetails, jobMatchScore);
    }

    /**
     * Factory for a fully populated response without structured details (backward compatible).
     */
    public static ApplicationAdvisorResponse of(
            ApplicationRecommendation recommendation,
            int score,
            List<String> strengths,
            List<String> concerns,
            List<String> recommendedActions,
            int jobMatchScore) {
        return new ApplicationAdvisorResponse(
                recommendation, score, strengths, concerns, recommendedActions, List.of(), jobMatchScore);
    }

    /**
     * Factory for a fully populated response without structured details (backward compatible).
     */
    public static ApplicationAdvisorResponse of(
            ApplicationRecommendation recommendation,
            int score,
            List<String> strengths,
            List<String> concerns,
            List<String> recommendedActions) {
        return new ApplicationAdvisorResponse(
                recommendation, score, strengths, concerns, recommendedActions, List.of(), 0);
    }
}

