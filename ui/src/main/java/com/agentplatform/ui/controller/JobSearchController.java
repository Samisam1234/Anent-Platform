package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.ui.dto.JobSearchRequestDto;
import com.agentplatform.ui.dto.JobSearchResponseDto;
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
 * REST controller exposing the existing job-search service.
 *
 * <p>{@code POST /api/v1/jobs/search} binds the optional filters into a
 * {@link JobSearchRequestDto}, delegates to {@link JobSearchService} (no LLM, no
 * repository, no source-specific logic here), and returns the existing
 * {@link JobSearchResult}-compatible {@link JobSearchResponseDto}.</p>
 *
 * <p>All filtering, normalization, URL validation and deduplication live in the
 * service/JobSource pipeline — this controller only binds, validates and maps.
 * Invalid request input throws so {@link GlobalExceptionHandler} maps it to a
 * RFC 7807 4xx; unexpected failures become a safe plain 500 (never a stack trace).</p>
 */
@RestController
@RequestMapping("/api/v1/jobs/search")
public class JobSearchController {

    private static final Logger log = LoggerFactory.getLogger(JobSearchController.class);

    private final JobSearchService jobSearchService;

    public JobSearchController(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @PostMapping
    public ResponseEntity<Object> search(@RequestBody(required = false) JobSearchRequestDto request) {
        try {
            JobSearchRequest domain = request != null ? request.toDomain() : JobSearchRequestDto.empty();
            JobSearchResult result = jobSearchService.search(domain);
            return ResponseEntity.ok(JobSearchResponseDto.from(result));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Job search failed unexpectedly: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Job search failed; please try again later.");
        }
    }
}