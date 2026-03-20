package dev.gimi.core.model.ff;

/** Operators for feature flag targeting rules. */
public enum TargetingOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    IN,
    NOT_IN,
    REGEX,
    SEMVER_GT,
    SEMVER_LT,
    PERCENTAGE
}
