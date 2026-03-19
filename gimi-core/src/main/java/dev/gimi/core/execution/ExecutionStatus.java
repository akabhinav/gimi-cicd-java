package dev.gimi.core.execution;

/**
 * Represents the possible states of a pipeline execution, stage, or step.
 */
public enum ExecutionStatus {

    /** The execution unit is queued but has not yet started. */
    PENDING,

    /** The execution unit is currently in progress. */
    RUNNING,

    /** The execution unit completed successfully. */
    PASSED,

    /** The execution unit completed with a failure. */
    FAILED,

    /** The execution unit was skipped (e.g. due to a condition or dependency failure). */
    SKIPPED,

    /** The execution unit was cancelled before completion. */
    CANCELLED
}
