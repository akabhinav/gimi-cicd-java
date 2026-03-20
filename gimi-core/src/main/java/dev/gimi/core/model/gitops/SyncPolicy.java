package dev.gimi.core.model.gitops;

/** GitOps sync policy for deployment management. */
public enum SyncPolicy {
    /** Manual sync — requires explicit trigger. */
    MANUAL,
    /** Auto-sync on Git changes. */
    AUTO,
    /** PR-based — creates PR for approval before sync. */
    PR_BASED
}
