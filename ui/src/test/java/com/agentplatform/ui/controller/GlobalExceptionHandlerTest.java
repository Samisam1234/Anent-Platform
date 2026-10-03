package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.application.JobApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Phase 12.9: optimistic-lock conflicts must surface as HTTP 409 Conflict
 * (not the generic 500), so the client can reload and retry.
 */
@DisplayName("GlobalExceptionHandler — optimistic-lock conflict mapping")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 Conflict ProblemDetail")
    void optimisticLockMapsTo409Conflict() {
        ProblemDetail problem = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(JobApplication.class, 1L));

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Conflict", problem.getTitle());
        assertNotNull(problem.getType());
        assertFalse(problem.getDetail().isBlank());
    }
}