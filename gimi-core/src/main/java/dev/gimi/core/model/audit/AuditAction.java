package dev.gimi.core.model.audit;

public final class AuditAction {
    private AuditAction() {}

    public static final String LOGIN = "user.login";
    public static final String LOGOUT = "user.logout";
    public static final String LOGIN_FAILED = "user.login_failed";
    public static final String USER_CREATED = "user.created";
    public static final String USER_UPDATED = "user.updated";
    public static final String USER_DELETED = "user.deleted";
    public static final String ROLE_ASSIGNED = "role.assigned";
    public static final String ROLE_REVOKED = "role.revoked";
    public static final String PIPELINE_CREATED = "pipeline.created";
    public static final String PIPELINE_UPDATED = "pipeline.updated";
    public static final String PIPELINE_DELETED = "pipeline.deleted";
    public static final String RUN_TRIGGERED = "run.triggered";
    public static final String RUN_CANCELLED = "run.cancelled";
    public static final String RUN_COMPLETED = "run.completed";
    public static final String APPROVAL_APPROVED = "approval.approved";
    public static final String APPROVAL_REJECTED = "approval.rejected";
    public static final String WORKER_REGISTERED = "worker.registered";
    public static final String WORKER_DEREGISTERED = "worker.deregistered";
    public static final String SECRET_ACCESSED = "secret.accessed";
    public static final String SECRET_CREATED = "secret.created";
    public static final String SECRET_UPDATED = "secret.updated";
    public static final String SECRET_DELETED = "secret.deleted";
    public static final String POLICY_EVALUATED = "policy.evaluated";
    public static final String POLICY_VIOLATED = "policy.violated";
    public static final String CONNECTOR_CREATED = "connector.created";
    public static final String CONNECTOR_TESTED = "connector.tested";
    public static final String FEATURE_FLAG_TOGGLED = "feature_flag.toggled";
    public static final String CONFIG_CHANGED = "config.changed";
    public static final String API_KEY_CREATED = "api_key.created";
    public static final String API_KEY_REVOKED = "api_key.revoked";
}
