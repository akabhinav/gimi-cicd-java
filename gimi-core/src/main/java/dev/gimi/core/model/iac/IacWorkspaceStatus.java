package dev.gimi.core.model.iac;

/** Lifecycle status of an IaC workspace. */
public enum IacWorkspaceStatus {
    INACTIVE,
    ACTIVE,
    PLANNING,
    APPLYING,
    DESTROYING,
    DRIFTED,
    ERROR
}
