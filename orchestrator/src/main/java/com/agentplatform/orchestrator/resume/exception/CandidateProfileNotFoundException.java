package com.agentplatform.orchestrator.resume.exception;

public class CandidateProfileNotFoundException
extends RuntimeException {
    public CandidateProfileNotFoundException(String message) {
        super(message);
    }

    public CandidateProfileNotFoundException(Long id) {
        super("Candidate profile with ID " + id + " was not found. Please upload a resume first.");
    }
}

