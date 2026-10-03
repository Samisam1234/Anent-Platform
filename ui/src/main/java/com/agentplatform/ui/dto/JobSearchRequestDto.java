package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.job.JobSearchRequest;

import java.util.List;

/**
 * Request body for POST /api/v1/jobs/search.
 *
 * <p>A thin binding DTO over {@link JobSearchRequest}. All fields are optional —
 * a blank/omitted filter means "no restriction" and is preserved by the service.
 * {@code limit} caps the page size (1–100), defaulting to 20 in the domain type.</p>
 *
 * <p>{@code keywords} remains accepted for API compatibility but is no longer surfaced in
 * the UI. {@code candidateProfileId} is the stored profile the Job Search page sends so the
 * service can derive relevance keywords from the parsed resume instead of asking the user
 * to type them.</p>
 */
public record JobSearchRequestDto(
        List<String> keywords,
        String location,
        String experience,
        String employmentType,
        String datePosted,
        Integer limit,
        String source,
        Long candidateProfileId
) {

    /**
     * Backwards-compatible constructor for callers that do not carry a candidate profile.
     */
    public JobSearchRequestDto(List<String> keywords, String location, String experience,
                               String employmentType, String datePosted, Integer limit, String source) {
        this(keywords, location, experience, employmentType, datePosted, limit, source, null);
    }

    /** Converts this DTO into the domain {@link JobSearchRequest} for the service. */
    public JobSearchRequest toDomain() {
        return new JobSearchRequest(
                keywords,
                location,
                experience,
                employmentType,
                datePosted,
                limit,
                source,
                candidateProfileId
        );
    }

    public static JobSearchRequest empty() {
        return new JobSearchRequest(List.of(), null, null, null, null, 20, null);
    }
}
