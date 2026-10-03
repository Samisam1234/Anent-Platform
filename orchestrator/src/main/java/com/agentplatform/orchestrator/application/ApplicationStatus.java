package com.agentplatform.orchestrator.application;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

public enum ApplicationStatus {

    DRAFT("DRAFT"),
    GENERATED("GENERATED"),
    UNDER_REVIEW("UNDER_REVIEW"),
    APPROVED_FOR_APPLICATION("APPROVED_FOR_APPLICATION"),
    REJECTED("REJECTED"),
    ARCHIVED("ARCHIVED");

    private final String status;

    ApplicationStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}