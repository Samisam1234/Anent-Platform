package com.agentplatform.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Proves the Phase 8.3 MDC contract: a runId set through {@link LoggingContext}
 * lands in the SLF4J MDC under a single {@code runId} key and is removed again
 * by {@link LoggingContext#clear()}.
 */
@DisplayName("LoggingContext — single runId MDC key")
class LoggingContextTest {

    @AfterEach
    void tearDown() {
        LoggingContext.clear();
    }

    @Test
    @DisplayName("setRunId inserts the runId into the MDC and exposes it via getRunId")
    void setRunIdPutsRunIdIntoMdc() {
        LoggingContext.setRunId("run-123");

        assertEquals("run-123", MDC.get(LoggingContext.RUN_ID_KEY));
        assertEquals("run-123", LoggingContext.getRunId());
    }

    @Test
    @DisplayName("clear removes the runId from the MDC")
    void clearRemovesRunIdFromMdc() {
        LoggingContext.setRunId("run-123");
        LoggingContext.clear();

        assertNull(MDC.get(LoggingContext.RUN_ID_KEY));
        assertNull(LoggingContext.getRunId());
    }

    @Test
    @DisplayName("null or blank runId clears the key instead of inserting garbage")
    void nullOrBlankRunIdClears() {
        LoggingContext.setRunId("run-123");
        LoggingContext.setRunId(null);
        assertNull(LoggingContext.getRunId());

        LoggingContext.setRunId("run-123");
        LoggingContext.setRunId("   ");
        assertNull(LoggingContext.getRunId());
    }
}