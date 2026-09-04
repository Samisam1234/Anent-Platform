package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.service.AgentChatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * Handles a missing candidate profile when matching jobs against a stored
     * resume.
     *
     * <p>Returns HTTP 404 Not Found.</p>
     */
    @ExceptionHandler(CandidateProfileNotFoundException.class)
    public ProblemDetail handleCandidateProfileNotFound(CandidateProfileNotFoundException ex) {
        log.warn("Candidate profile not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Candidate Profile Not Found");
        problem.setType(URI.create("https://agentplatform.local/errors/candidate-profile-not-found"));
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

    /**
     * Handles a missing job when orchestrating against an unknown job id.
     *
     * <p>Returns HTTP 404 Not Found.</p>
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ProblemDetail handleJobNotFound(JobNotFoundException ex) {
        log.warn("Job not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Job Not Found");
        problem.setType(URI.create("https://agentplatform.local/errors/job-not-found"));
        return problem;
    }

    /**
     * Last-resort handler for unexpected server failures.
     *
     * <p>Returns HTTP 500 Internal Server Error with a generic, safe message —
     * never leaking the underlying exception class, message, or stack trace.</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled server error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later."
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://agentplatform.local/errors/internal-server-error"));
        return problem;
    }

    /**
     * Malformed/unreadable request body.
     *
     * <p>Returns HTTP 400 Bad Request.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed or unreadable request body: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed or unreadable request body."
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://agentplatform.local/errors/bad-request"));
        return problem;
    }

    /**
     * A request path with no matching handler.
     *
     * <p>Returns HTTP 404 Not Found. This protects the generic handler by keeping
     * genuinely missing resources mapped to 404 instead of an opaque 500.</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getResourcePath());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The requested resource was not found."
        );
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://agentplatform.local/errors/not-found"));
        return problem;
    }

    /**
     * A valid path invoked with an unsupported HTTP method.
     *
     * <p>Returns HTTP 405 Method Not Allowed.</p>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed for path: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this resource."
        );
        problem.setTitle("Method Not Allowed");
        problem.setType(URI.create("https://agentplatform.local/errors/method-not-allowed"));
        return problem;
    }

    /**
     * An unsupported media type.
     *
     * <p>Returns HTTP 415 Unsupported Media Type.</p>
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type."
        );
        problem.setTitle("Unsupported Media Type");
        problem.setType(URI.create("https://agentplatform.local/errors/unsupported-media-type"));
        return problem;
    }

    /**
     * A path/method argument of the wrong type.
     *
     * <p>Returns HTTP 400 Bad Request.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Argument type mismatch: {}", ex.getName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameter."
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://agentplatform.local/errors/bad-request"));
        return problem;
    }
}