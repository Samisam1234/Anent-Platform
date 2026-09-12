package com.agentplatform.orchestrator.matching;

/**
 * Career tracks understood by the matching pipeline.
 *
 * <p>The model is deliberately two-level. The fine-grained tracks
 * ({@link #SOFTWARE}, {@link #EMBEDDED}, {@link #VLSI_FPGA}, {@link #AI_ML}) describe what
 * a role or a candidate actually is, and are what relevance filtering reasons about — a
 * VLSI listing must not be treated as interchangeable with an embedded-firmware one, and
 * AI/ML is its own discipline rather than generic software.</p>
 *
 * <p>The coarse values ({@link #HARDWARE}, {@link #MIXED}, {@link #UNKNOWN}) are retained
 * because a large amount of existing behaviour is expressed at that level: track filters,
 * explanations, tailoring preferences and gap analysis. {@link #family()} bridges the two,
 * so a filter written against {@code HARDWARE} still accepts {@code VLSI_FPGA} and
 * {@code EMBEDDED} without those tracks having to collapse into it.</p>
 */
public enum CareerTrack {
    /** Application/backend/full-stack software engineering. */
    SOFTWARE,
    /** Embedded systems and firmware engineering. */
    EMBEDDED,
    /** VLSI, RTL, FPGA and ASIC design/verification. */
    VLSI_FPGA,
    /** Machine learning, deep learning, computer vision, AI engineering. */
    AI_ML,
    /** Coarse hardware family — retained for compatibility and for genuinely
     *  hardware roles that are neither embedded nor VLSI/FPGA specific. */
    HARDWARE,
    /** A role or profile that spans more than one family. */
    MIXED,
    /** Not determinable from the available signals. */
    UNKNOWN;

    /**
     * The coarse family this track belongs to.
     *
     * <p>{@code EMBEDDED} and {@code VLSI_FPGA} are hardware disciplines; {@code AI_ML} is
     * a software discipline. Coarse values map to themselves. Callers that only reason at
     * family level should compare against this rather than the raw track.</p>
     */
    public CareerTrack family() {
        return switch (this) {
            case EMBEDDED, VLSI_FPGA -> HARDWARE;
            case AI_ML -> SOFTWARE;
            default -> this;
        };
    }

    public boolean isHardwareFamily() {
        return family() == HARDWARE;
    }

    public boolean isSoftwareFamily() {
        return family() == SOFTWARE;
    }

    /** Whether this track is specific enough to make a relevance judgement. */
    public boolean isSpecific() {
        return this != UNKNOWN && this != MIXED;
    }

    /**
     * Whether this track satisfies a filter that may itself be expressed at either level.
     *
     * <p>A {@code HARDWARE} filter accepts {@code VLSI_FPGA} and {@code EMBEDDED}; a
     * {@code VLSI_FPGA} filter accepts only {@code VLSI_FPGA}. {@code MIXED} always
     * satisfies a filter because it genuinely spans families, preserving the previous
     * behaviour. {@code UNKNOWN} never satisfies a specific filter — an unclassifiable
     * listing must not be pulled into a discipline-specific result set.</p>
     */
    public boolean satisfies(CareerTrack filter) {
        if (filter == null || filter == UNKNOWN) {
            return true;
        }
        if (this == MIXED) {
            return true;
        }
        if (this == UNKNOWN) {
            return false;
        }
        return this == filter || family() == filter;
    }

    public static CareerTrack fromString(String track) {
        if (track == null || track.isBlank()) {
            return UNKNOWN;
        }
        try {
            return CareerTrack.valueOf(track.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
