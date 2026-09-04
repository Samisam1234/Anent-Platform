package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchResult;

import java.util.List;

/**
 * Response body for POST /api/v1/jobs/search.
 *
 * <p>A thin DTO mirroring {@link JobSearchResult} exactly so the client receives
 * the established structure ({@code jobs}, {@code total}, {@code source},
 * {@code live}, {@code message}) and reaches into each {@link Job} for provenance
 * and the safe {@code sourceUrl}. The backing service keeps all filtering, URL
 * validation and deduplication logic; this DTO only carries the result out.</p>
 */
public record JobSearchResponseDto(
        List<Job> jobs,
        int total,
        String source,
        boolean live,
        String message
) {

    public static JobSearchResponseDto from(JobSearchResult result) {
        return new JobSearchResponseDto(
                result.jobs(),
                result.total(),
                result.source(),
                result.live(),
                result.message()
        );
    }
}