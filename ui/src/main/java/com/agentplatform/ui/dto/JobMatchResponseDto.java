package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchResult;

import java.util.List;

/**
 * Response body for POST /api/v1/jobs/match.
 *
 * <p>A thin DTO around {@link JobMatchResult} exposing the ranked, scored
 * matches along with the candidate and search metadata so the client does not
 * reach into the domain type.</p>
 */
public record JobMatchResponseDto(
        Long candidateProfileId,
        String candidateName,
        int totalJobs,
        List<JobMatch> matches,
        String source,
        boolean live,
        String message
) {

    public static JobMatchResponseDto from(JobMatchResult result) {
        return new JobMatchResponseDto(
                result.candidateProfileId(),
                result.candidateName(),
                result.totalJobs(),
                result.matches(),
                result.source(),
                result.live(),
                result.message()
        );
    }
}