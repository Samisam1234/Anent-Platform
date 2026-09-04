package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing single-job details at {@code GET /api/v1/jobs/{jobId}}.
 *
 * <p>Delegates entirely to {@link JobSearchService#findById(String)} — the existing
 * service already iterates available {@code JobSource}s with per-source failure
 * isolation, and URL safety (mock → null, invalid/unsafe external → null, validated
 * external → preserved) is guaranteed inside the JobSource pipeline. The controller
 * adds no source-specific logic, invented fields, or replacement URLs.</p>
 *
 * <p>Unknown/blank ids yield HTTP 404 (empty body, no stack trace). Unexpected service
 * failures become a safe plain 500 string — never the internal exception message.</p>
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobDetailsController {

    private static final Logger log = LoggerFactory.getLogger(JobDetailsController.class);

    private final JobSearchService jobSearchService;

    public JobDetailsController(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Object> getJob(@PathVariable String jobId) {
        try {
            return jobSearchService.findById(jobId)
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Job lookup failed unexpectedly: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Job lookup failed; please try again later.");
        }
    }
}