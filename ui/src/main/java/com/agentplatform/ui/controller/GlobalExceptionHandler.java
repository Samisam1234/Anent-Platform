package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.service.AgentChatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Centralised exception handler for all {@link RestController} advice.
 *
 * <p>Uses RFC 7807 {@link ProblemDetail} (built into Spring 6) for structured
 * error responses, so clients receive consistent JSON error payloads.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles failures communicating with the Ollama backend.
     *
     * <p>Returns HTTP 503 Service Unavailable with a user-friendly message.</p>
     */
    @ExceptionHandler(AgentChatException.class)
    public ProblemDetail handleAgentChatException(AgentChatException ex) {
        log.error("Ollama communication error: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage()
        );
        problem.setTitle("AI Model Unavailable");
        problem.setType(URI.create("https://agentplatform.local/errors/model-unavailable"));
        return problem;
    }

    /**
     * Handles bad input (blank query etc.).
     *
     * <p>Returns HTTP 400 Bad Request.</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://agentplatform.local/errors/bad-request"));
        return problem;
    }
}
