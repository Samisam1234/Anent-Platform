package com.agentplatform.orchestrator.advisor;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;

import java.util.List;

/**
 * Request model for the Application Advisor foundation service.
 *
 * <p>Carries only safe identifiers or existing domain models resolved through
 * the persistence/job-source layers — the client cannot fabricate trusted
 * {@code CandidateProfile} or {@code Job} objects.</p>
 *
 * <p>Follows the project's established pattern of identifier-only input with
 * server-side resolution (see {@code OrchestrationRequestDto}).</p>
 */
public record ApplicationAdvisorRequest(
        Long candidateId,
        String jobId
) {

    public static final int MAX_JOB_ID_LENGTH = 200;

    /**
     * Validates the request, throwing {@link IllegalArgumentException} for
     * missing or invalid input. Maps to HTTP 400 via the global exception handler
     * when used at an API boundary.
     */
    public static void validate(ApplicationAdvisorRequest request) {
        if (request == null || request.candidateId() == null || request.candidateId() <= 0) {
            throw new IllegalArgumentException("A positive candidateId is required.");
        }
        if (request.jobId() == null || request.jobId().isBlank()) {
            throw new IllegalArgumentException("jobId is required.");
        }
        if (request.jobId().length() > MAX_JOB_ID_LENGTH) {
            throw new IllegalArgumentException("jobId must be at most "
                    + MAX_JOB_ID_LENGTH + " characters.");
        }
    }

    /**
     * Creates a request from resolved domain models (used internally by services
     * that already have the profile and job available).
     */
    public static ApplicationAdvisorRequest fromDomain(Long candidateId, String jobId) {
        return new ApplicationAdvisorRequest(candidateId, jobId);
    }
}

