package dev.gimi.core.model.infra;

/** Actions for infrastructure provisioner steps. */
public enum ProvisionerAction {
    INIT,
    PLAN,
    APPLY,
    DESTROY,
    VALIDATE,
    DRIFT_DETECT,
    COST_ESTIMATE,
    OUTPUT
}
