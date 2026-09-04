package com.agentplatform.orchestrator.job;

public class JobSearchException
extends RuntimeException {
    public JobSearchException(String message) {
        super(message);
    }

    public JobSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}

