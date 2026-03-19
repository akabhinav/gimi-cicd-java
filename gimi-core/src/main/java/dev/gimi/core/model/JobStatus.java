package dev.gimi.core.model;

/**
 * Status of a job in the distributed queue.
 */
public enum JobStatus {
    QUEUED,
    ASSIGNED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
