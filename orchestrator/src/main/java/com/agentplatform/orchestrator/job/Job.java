package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.time.Instant;

/**
 * Immutable domain model for a job listing.
 *
 * <p>Carries both user-facing facets (title, company, description) and
 * cross-source provenance. {@code requiredSkills} and {@code preferredSkills}
 * are defensively copied to immutable lists.</p>
 *
 * <h2>Listing URL vs employer application URL</h2>
 * <p>These are deliberately separate fields, because they are different things:</p>
 * <ul>
 *   <li>{@code sourceUrl} — where this platform found the listing (for example the
 *       aggregator's own job page). Always the provenance link.</li>
 *   <li>{@code applicationUrl} — where a candidate actually applies, when the source
 *       supplies such a destination. {@code null} means the source did not provide
 *       one; it must never be back-filled with {@code sourceUrl}, and the UI must
 *       then say the employer application link is unavailable rather than implying
 *       the aggregator page is the application.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Job(
        String id,
        String title,
        String company,
        String location,
        String description,
        List<String> requiredSkills,
        List<String> preferredSkills,
        String experienceRequirement,
        String employmentType,
        String postingDate,
        String source,
        String sourceUrl,
        String sourceType,
        Instant discoveredAt,
        /**
         * The employer's own application destination, or {@code null} when the source
         * does not provide one. Never derived from {@code sourceUrl}.
         */
        String applicationUrl
) {
    public Job {
        requiredSkills = requiredSkills != null ? List.copyOf(requiredSkills) : List.of();
        preferredSkills = preferredSkills != null ? List.copyOf(preferredSkills) : List.of();
        sourceType = sourceType == null ? "UNKNOWN" : sourceType;
    }

    /**
     * Backwards-compatible 14-argument constructor for every existing caller, test and
     * serialized payload that pre-dates {@code applicationUrl}. Such listings have no
     * known employer application destination, which is exactly what {@code null} means.
     */
    public Job(
            String id,
            String title,
            String company,
            String location,
            String description,
            List<String> requiredSkills,
            List<String> preferredSkills,
            String experienceRequirement,
            String employmentType,
            String postingDate,
            String source,
            String sourceUrl,
            String sourceType,
            Instant discoveredAt) {
        this(id, title, company, location, description, requiredSkills, preferredSkills,
                experienceRequirement, employmentType, postingDate, source, sourceUrl,
                sourceType, discoveredAt, null);
    }
}
