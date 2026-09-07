package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.advisor.ApplicationAdvisorResponse;
import com.agentplatform.orchestrator.advisor.ApplicationAdvisorService;
import com.agentplatform.orchestrator.advisor.ApplicationAdvisorRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the Application Advisor endpoint.
 *
 * <p>Endpoint: {@code POST /api/v1/jobs/advisor}. Evaluates a candidate against a
 * specific job and returns structured application advice. This endpoint is a
 * pure API boundary: it resolves input purely by identifier through existing
 * services and invokes the {@link ApplicationAdvisorService}. It never
 * triggers email sending, never accepts arbitrary tool names, and never makes
 * extra LLM calls. No state is persisted here.</p>
 */
@RestController
@RequestMapping("/api/v1/jobs/advisor")
public class ApplicationAdvisorController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAdvisorController.class);

    private final ApplicationAdvisorService applicationAdvisorService;

    public ApplicationAdvisorController(ApplicationAdvisorService applicationAdvisorService) {
        this.applicationAdvisorService = applicationAdvisorService;
    }

    /**
     * Evaluates a candidate against a job and returns structured application advice.
     *
     * <p>Request body:
     * <pre>{@code {"candidateId": 1, "jobId": "job-123"}}</pre>
     *
     * <p>Response body (200 OK): an {@link ApplicationAdvisorResponse}. Error
     * responses (400/404/500) are RFC 7807 {@code ProblemDetail}s produced by
     * {@link GlobalExceptionHandler}.</p>
     *
     * @param request the advisor request (ids only)
     * @return 200 OK with the structured advice result
     */
    @PostMapping
    public ResponseEntity<com.agentplatform.orchestrator.advisor.ApplicationAdvisorResponse> advise(
            @RequestBody com.agentplatform.orchestrator.advisor.ApplicationAdvisorRequest request) {
        ApplicationAdvisorRequest.validate(request);

        if (log.isDebugEnabled()) {
            log.debug("Application advisor request for candidateId={} jobId={}",
                    request.candidateId(), request.jobId());
        }

        ApplicationAdvisorResponse response = applicationAdvisorService.advise(request);
        return ResponseEntity.ok(response);
    }
}