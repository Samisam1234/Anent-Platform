package com.agentplatform.orchestrator.resume;

import com.agentplatform.orchestrator.resume.SkillTaxonomy.Category;

import java.util.List;

/**
 * Traceable evidence explaining why a career track was detected for a candidate.
 *
 * <p>Carries the deterministic weighted score and the canonical skills that
 * contributed to the detection. Tracks are inferred from skill evidence, never
 * from degree names alone.</p>
 */
public record CareerTrackEvidence(
        String track,
        double score,
        List<String> contributingSkills
) {
    public CareerTrackEvidence {
        track = track == null ? "" : track.trim();
        contributingSkills = contributingSkills != null ? List.copyOf(contributingSkills) : List.of();
    }

    /** Maps a taxonomy {@link Category} to a human-readable track label. */
    public static String trackLabelForCategory(Category category) {
        if (category == null) return "Software Engineering";
        return switch (category) {
            case SOFTWARE -> "Software Engineering";
            case PROGRAMMING_AI -> "AI / ML";
            case EMBEDDED -> "Embedded Systems";
            case VLSI_FPGA -> "VLSI / FPGA";
            case COMMUNICATION -> "Electronics / ECE";
        };
    }
}
