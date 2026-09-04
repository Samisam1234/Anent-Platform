package com.agentplatform.orchestrator.job.exception;

/**
 * Raised when a requested job cannot be resolved through the job sources.
 * Carries only a safe, user-facing message — never raw exception details.
 */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String id) {
        super("Job with ID '" + id + "' was not found.");
    }
}
