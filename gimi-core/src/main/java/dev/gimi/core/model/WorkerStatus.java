package dev.gimi.core.model;

/**
 * Status of a worker node in the cluster.
 */
public enum WorkerStatus {
    ONLINE,
    BUSY,
    DRAINING,
    OFFLINE,
    DEAD
}
