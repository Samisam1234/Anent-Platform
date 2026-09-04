package com.agentplatform.orchestrator.matching;

public class JobMatchingException
extends RuntimeException {
    public JobMatchingException(String message) {
        super(message);
    }

    public JobMatchingException(String message, Throwable cause) {
        super(message, cause);
    }
}

