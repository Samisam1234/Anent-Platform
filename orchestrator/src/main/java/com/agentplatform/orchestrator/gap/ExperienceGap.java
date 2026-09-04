package com.agentplatform.orchestrator.gap;

/**
 * Deterministic experience shortfall between a job's stated requirement and the
 * candidate's structured experience.
 *
 * <p>Only computed when <em>both</em> sides carry structured, parseable years
 * ("X years", "X+ years", "X-Y years", or angle entry-level markers). If either side
 * is not deterministically known, {@code knowable} is {@code false} and
 * {@code gapYears} is {@code null} — no years are ever invented from project duration,
 * graduation dates, or vague prose.</p>
 */
public record ExperienceGap(
        Integer requiredYears,
        Integer candidateYears,
        Integer gapYears,
        boolean knowable
) {
    public ExperienceGap {
        gapYears = knowable && gapYears != null ? gp(gapYears) : null;
    }

    private static Integer gp(Integer v) {
        return v < 0 ? 0 : v;
    }

    /** Whether this gap is meaningful enough to surface as an improvement priority. */
    public boolean hasShortfall() {
        return knowable && gapYears != null && gapYears > 0;
    }
}