package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.agent.OrchestrationRun;
import com.agentplatform.orchestrator.service.OrchestrationService;
import com.agentplatform.ui.dto.OrchestrationRequestDto;
import com.agentplatform.ui.dto.OrchestrationRunResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the controlled career-orchestration endpoint.
 *
 * <p>Endpoint: {@code POST /api/v1/agent/orchestrate}. Starts an orchestration
 * run and returns the structured execution result. Crucially, this endpoint is
 * a pure API boundary: it resolves input purely by identifier through existing
 * services and invokes the {@link CareerAgentOrchestrator} unchanged. It never
 * triggers email sending, never accepts arbitrary tool names, and never makes
 * extra LLM calls. No state is persisted here.</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class OrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationController.class);

    private final OrchestrationService orchestrationService;

    public OrchestrationController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * Starts an orchestration run from safe identifiers.
     *
     * <p>Request body:
     * <pre>{@code {"candidateId": 1, "jobId": "job-123"}}</pre>
     *
     * <p>Response body (200 OK): an {@link OrchestrationRunResponseDto}. Error
     * responses (400/404/500) are RFC 7807 {@code ProblemDetail}s produced by
     * {@link GlobalExceptionHandler}.</p>
     *
     * @param request the orchestration request (ids only)
     * @return 200 OK with the structured execution result
     */
    @PostMapping("/orchestrate")
    public ResponseEntity<OrchestrationRunResponseDto> orchestrate(
            @RequestBody OrchestrationRequestDto request) {
        OrchestrationRequestDto.validate(request);

        if (log.isDebugEnabled()) {
            log.debug("Orchestration request for candidateId={} jobId={}",
                    request.candidateId(), request.jobId());
        }

        OrchestrationRun run = orchestrationService.orchestrate(
                request.candidateId(), request.jobId());
        return ResponseEntity.ok(OrchestrationRunResponseDto.from(run));
    }
}
