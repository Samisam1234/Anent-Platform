package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.ui.dto.JobMatchRequestDto;
import com.agentplatform.ui.dto.JobMatchResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing deterministic resume→job matching.
 *
 * <p>{@code POST /api/v1/jobs/match} takes a {@link JobMatchRequestDto}
 * (stored {@code candidateProfileId} or inline {@code profile}, optional
 * {@code jobs} else the deterministic search catalog) and delegates to
 * {@link JobMatchingService}, returning ranked, scored {@link JobMatchResponseDto}.</p>
 *
 * <p>Domain failures raised by matching (missing profile → 404, invalid
 * parameters → 400) are re-thrown so {@link GlobalExceptionHandler} maps them
 * to RFC 7807 problem details; unexpected failures are returned as a plain
 * 500 string.</p>
 */
@RestController
@RequestMapping("/api/v1/jobs/match")
public class JobMatchController {

    private static final Logger log = LoggerFactory.getLogger(JobMatchController.class);

    private final JobMatchingService jobMatchingService;

    public JobMatchController(JobMatchingService jobMatchingService) {
        this.jobMatchingService = jobMatchingService;
    }

    @PostMapping
    public ResponseEntity<Object> match(@RequestBody JobMatchRequestDto request) {
        try {
            JobMatchResult result = jobMatchingService.matchJobs(request.toDomain());
            return ResponseEntity.ok(JobMatchResponseDto.from(result));
        } catch (CandidateProfileNotFoundException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Job matching failed unexpectedly: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Job matching failed; please try again later.");
        }
    }
}