package com.agentplatform.orchestrator.agent;

/**
 * Execution status of an agent within the orchestration pipeline.
 *
 * <p>This is a finite, simple state machine — there is no retry, no
 * infinite/looping state, and no background execution.</p>
 */
public enum AgentStatus {

    /** Not yet started. */
    PENDING,

    /** Currently executing. */
    RUNNING,

    /** Finished successfully with a usable result. */
    COMPLETED,

    /** Finished with a safe, non-fatal error. */
    FAILED,

    /** Skipped because a required input was absent or a blocking dependency failed. */
    SKIPPED
}
