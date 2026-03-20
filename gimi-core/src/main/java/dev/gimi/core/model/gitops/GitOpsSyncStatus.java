package dev.gimi.core.model.gitops;

/** Status of a GitOps sync operation. */
public enum GitOpsSyncStatus {
    SYNCED,
    OUT_OF_SYNC,
    PROGRESSING,
    DEGRADED,
    HEALTHY,
    MISSING,
    UNKNOWN
}
