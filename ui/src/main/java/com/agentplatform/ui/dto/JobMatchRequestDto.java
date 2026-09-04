package com.agentplatform.ui.dto;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.JobMatchRequest;
import com.agentplatform.orchestrator.resume.CandidateProfile;

import java.util.List;

/**
 * Request body for POST /api/v1/jobs/match.
 *
 * <p>Accepts either a stored {@code candidateProfileId} or an inline
 * {@code profile}; {@code jobs} is optional (else the deterministic search
 * catalog is used). Filtering/sorting via {@code keywords}, {@code location},
 * {@code experience}, {@code employmentType}, {@code careerTrack},
 * {@code limit} and {@code minScore}.</p>
 */
public record JobMatchRequestDto(
        Long candidateProfileId,
        List<String> keywords,
        String location,
        String experience,
        String employmentType,
        Integer limit,
        Integer minScore,
        CareerTrack careerTrack,
        CandidateProfile profile,
        List<Job> jobs
) {

    /** Converts this DTO into the domain {@link JobMatchRequest} for the service. */
    public JobMatchRequest toDomain() {
        return new JobMatchRequest(
                candidateProfileId,
                keywords,
                location,
                experience,
                employmentType,
                limit,
                minScore,
                careerTrack,
                profile,
                jobs
        );
    }
}