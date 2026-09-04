package com.agentplatform.ui.dto;

/**
 * Request body for {@code POST /api/v1/agent/orchestrate}.
 *
 * <p>Carries only safe identifiers. The candidate profile and job are resolved
 * server-side through the persistence/job-source layers — the client cannot
 * fabricate a trusted {@code CandidateProfile}/{@code Job} or trigger email
 * sending with this contract.</p>
 *
 * @param candidateId stored candidate profile id (positive)
 * @param jobId       job id (non-blank)
 */
public record OrchestrationRequestDto(Long candidateId, String jobId) {

    public static final int MAX_JOB_ID_LENGTH = 200;

    /**
     * Validates the request, throwing {@link IllegalArgumentException} (mapped to
     * HTTP 400 by the global handler) for missing/invalid input.
     */
    public static void validate(OrchestrationRequestDto request) {
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
}
