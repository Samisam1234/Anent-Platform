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
        Instant discoveredAt
) {
    public Job {
        requiredSkills = requiredSkills != null ? List.copyOf(requiredSkills) : List.of();
        preferredSkills = preferredSkills != null ? List.copyOf(preferredSkills) : List.of();
        sourceType = sourceType == null ? "UNKNOWN" : sourceType;
    }
}