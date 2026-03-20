package dev.gimi.core.model.iac;

/** Actions that can be performed on an IaC workspace. */
public enum IacAction {
    INIT,
    VALIDATE,
    PLAN,
    APPLY,
    DESTROY,
    DRIFT_DETECT,
    COST_ESTIMATE,
    IMPORT,
    STATE_PULL,
    STATE_PUSH
}
