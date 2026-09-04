package com.agentplatform.orchestrator.gap;

/**
 * A single deterministic, ranked improvement area for a candidate versus a job.
 *
 * <p>Each priority is self-explanatory and LLM-ready: {@code focus} names the
 * subject (a canonical skill, or the experience shortfall),
 * {@code type} classifies it, {@code reason} gives the factual rationale, and
 * {@code description} is a full human-readable sentence (see the {@code gap}
 * service for the exact wording). Ranks are 1-based and consecutive, ordered:
 * missing required skills (alphabetical) first, then a knowable experience
 * shortfall, then missing preferred skills.</p>
 *
 * <p>Everything is deterministic and factual. A missing skill is never claimed to
 * be present, experience is never invented, and no courses, URLs, certifications,
 * salaries, timelines or external resources are produced here — those belong to a
 * later learning-plan layer.</p>
 */
public record ImprovementPriority(
        int rank,
        String focus,
        String type,
        String reason,
        String description
) {
    public static final String TYPE_REQUIRED_SKILL = "REQUIRED_SKILL";
    public static final String TYPE_PREFERRED_SKILL = "PREFERRED_SKILL";
    public static final String TYPE_EXPERIENCE = "EXPERIENCE";

    public static final String REASON_REQUIRED = "required by target job";
    public static final String REASON_PREFERRED = "preferred by target job";
    public static final String REASON_EXPERIENCE = "experience shortfall";

    /** Subject label used by the experience priority (honed, not a skill). */
    public static final String FOCUS_EXPERIENCE = "Experience";

    public ImprovementPriority {
        focus = focus == null ? "" : focus.trim();
        type = type == null ? TYPE_REQUIRED_SKILL : type;
        reason = reason == null ? "" : reason.trim();
        description = description == null ? "" : description.trim();
    }

    /**
     * A missing required skill. {@code skill} is already canonicalized by the caller.
     */
    public static ImprovementPriority requiredSkill(int rank, String skill) {
        return new ImprovementPriority(rank, skill, TYPE_REQUIRED_SKILL, REASON_REQUIRED,
                skill + " is required by the target job but is not present in the candidate profile.");
    }

    /**
     * A missing preferred skill. {@code skill} is already canonicalized by the caller.
     */
    public static ImprovementPriority preferredSkill(int rank, String skill) {
        return new ImprovementPriority(rank, skill, TYPE_PREFERRED_SKILL, REASON_PREFERRED,
                skill + " is preferred by the target job but is currently missing.");
    }

    /**
     * A knowable experience shortfall, using only the structured values supplied.
     */
    public static ImprovementPriority experience(int rank, int requiredYears,
                                                 int candidateYears, int gapYears) {
        String description = "Target role requires " + requiredYears + " year"
                + (requiredYears == 1 ? "" : "s")
                + " of experience; candidate has " + candidateYears + " year"
                + (candidateYears == 1 ? "" : "s") + ".";
        return new ImprovementPriority(rank, FOCUS_EXPERIENCE, TYPE_EXPERIENCE, REASON_EXPERIENCE,
                description);
    }

    /** {@code gapYears} shortfall, kept for callers that only need the delta. */
    public static ImprovementPriority experience(int rank, int gapYears) {
        return new ImprovementPriority(rank, FOCUS_EXPERIENCE, TYPE_EXPERIENCE, REASON_EXPERIENCE,
                "Gain " + gapYears + " year" + (gapYears == 1 ? "" : "s")
                        + " more professional experience.");
    }
}