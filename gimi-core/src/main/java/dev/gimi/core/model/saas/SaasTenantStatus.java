package dev.gimi.core.model.saas;

/** Lifecycle status of a SaaS tenant. */
public enum SaasTenantStatus {
    PROVISIONING,
    ACTIVE,
    TRIAL,
    SUSPENDED,
    DEACTIVATED,
    MIGRATING,
    MAINTENANCE
}
