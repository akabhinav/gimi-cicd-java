package dev.gimi.core.auth;

/**
 * User roles for RBAC.
 */
public enum Role {
    ADMIN,       // Full access
    OPERATOR,    // Can run/approve pipelines
    DEVELOPER,   // Can trigger builds, view logs
    VIEWER       // Read-only access
}
