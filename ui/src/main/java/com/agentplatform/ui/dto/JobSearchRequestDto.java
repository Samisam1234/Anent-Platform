package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.job.JobSearchRequest;

import java.util.List;

/**
 * Request body for POST /api/v1/jobs/search.
 *
 * <p>A thin binding DTO over {@link JobSearchRequest}. All fields are optional —
 * a blank/omitted filter means "no restriction" and is preserved by the service.
 * {@code limit} caps the page size (1–100), defaulting to 20 in the domain type.</p>
 */
public record JobSearchRequestDto(
        List<String> keywords,
        String location,
        String experience,
        String employmentType,
        String datePosted,
        Integer limit,
        String source
) {

    /** Converts this DTO into the domain {@link JobSearchRequest} for the service. */
    public JobSearchRequest toDomain() {
        return new JobSearchRequest(
                keywords,
                location,
                experience,
                employmentType,
                datePosted,
                limit,
                source
        );
    }

    public static JobSearchRequest empty() {
        return new JobSearchRequest(List.of(), null, null, null, null, 20, null);
    }
}