package com.agentplatform.orchestrator.agent;

/**
 * Deterministic lifecycle status of a single orchestration run.
 *
 * <p>Statuses are derived from the actual agent outcomes (never invented):
 * a blocking failure fails the run, an optional-agent failure makes it partial,
 * and a run with no completed work is partial. Skips caused by missing input are
 * legitimate and do not by themselves fail a run.</p>
 */
public enum RunStatus {

    /** A run object has been created but orchestration has not started. */
    PENDING,

    /** Orchestration is currently executing agents. */
    RUNNING,

    /** All agents that could run completed; no failures (skips allowed). */
    COMPLETED,

    /** An optional agent failed, or no usable work was produced. */
    PARTIAL,

    /** A blocking agent (RESUME / JOB_DISCOVERY) failed. */
    FAILED
}
