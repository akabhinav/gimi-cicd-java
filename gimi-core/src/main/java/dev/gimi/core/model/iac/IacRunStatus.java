package dev.gimi.core.model.iac;

/** Status of an IaC run. */
public enum IacRunStatus {
    PENDING,
    INITIALIZING,
    PLANNING,
    AWAITING_APPROVAL,
    APPLYING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
