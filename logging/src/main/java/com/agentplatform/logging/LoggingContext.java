package com.agentplatform.logging;

import org.slf4j.MDC;

/**
 * Static facade over SLF4J's {@link MDC} that correlates all log lines of a
 * single logical unit of work (an orchestration run, a chat turn, a custom
 * process request) under one {@code runId} key.
 *
 * <p>Callers must clear the context in a {@code finally} block so a runId never
 * leaks into unrelated threads/log lines. The runId is rendered by the shared
 * {@code logback-spring.xml} console pattern and is available to any
 * {@link org.slf4j.Logger} invoked on the same thread while it is set.</p>
 */
public final class LoggingContext {

    /** The single MDC key used for run correlation. */
    public static final String RUN_ID_KEY = "runId";

    private LoggingContext() {
    }

    /** Puts {@code runId} into the MDC. A null/blank runId clears the key. */
    public static void setRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            MDC.remove(RUN_ID_KEY);
        } else {
            MDC.put(RUN_ID_KEY, runId);
        }
    }

    /** Returns the current {@code runId}, or {@code null} when none is set. */
    public static String getRunId() {
        return MDC.get(RUN_ID_KEY);
    }

    /** Removes the {@code runId} key from the MDC. */
    public static void clear() {
        MDC.remove(RUN_ID_KEY);
    }
}