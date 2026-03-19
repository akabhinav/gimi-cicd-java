package dev.gimi.core.auth;

/**
 * Fine-grained permissions for RBAC.
 */
public enum Permission {
    PIPELINE_READ,
    PIPELINE_WRITE,
    PIPELINE_EXECUTE,
    PIPELINE_DELETE,
    RUN_VIEW,
    RUN_CANCEL,
    APPROVAL_MANAGE,
    WORKER_MANAGE,
    ARTIFACT_READ,
    ARTIFACT_WRITE,
    USER_MANAGE,
    SYSTEM_ADMIN;

    /**
     * Returns the permissions associated with a role.
     */
    public static java.util.Set<Permission> forRole(Role role) {
        return switch (role) {
            case ADMIN -> java.util.Set.of(values());
            case OPERATOR -> java.util.Set.of(
                PIPELINE_READ, PIPELINE_WRITE, PIPELINE_EXECUTE,
                RUN_VIEW, RUN_CANCEL, APPROVAL_MANAGE,
                WORKER_MANAGE, ARTIFACT_READ, ARTIFACT_WRITE
            );
            case DEVELOPER -> java.util.Set.of(
                PIPELINE_READ, PIPELINE_EXECUTE,
                RUN_VIEW, ARTIFACT_READ, ARTIFACT_WRITE
            );
            case VIEWER -> java.util.Set.of(
                PIPELINE_READ, RUN_VIEW, ARTIFACT_READ
            );
        };
    }
}
