package com.agentplatform.orchestrator.tailoring;

/**
 * A deterministic, advisory estimate of how resume-ready a candidate is for a specific
 * job.
 *
 * <p>This is explicitly <em>not</em> a guarantee of ATS success. The score is computed
 * from the authoritative {@code CareerGapAnalysis} matched/missing counts plus
 * deterministic evidence of relevant projects and experience, and is documented in
 * {@link ResumeTailoringAnalysisService}. It never alters existing job-match scores.</p>
 */
public record AtsReadinessAnalysis(
        int score,
        String label,
        int matchedRequiredCount,
        int missingRequiredCount,
        int matchedPreferredCount,
        int missingPreferredCount,
        boolean relevantProjectEvidence,
        boolean relevantExperienceEvidence,
        String explanation
) {
    public AtsReadinessAnalysis {
        label = label == null ? "" : label.trim();
        explanation = explanation == null ? "" : explanation.trim();
        score = Math.max(0, Math.min(100, score));
    }

    /** Whether there is no required-skill gap at all. */
    public boolean hasCompleteRequiredCoverage() {
        return missingRequiredCount == 0;
    }
}