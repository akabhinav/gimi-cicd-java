package dev.gimi.core.model.verification;

/** How metric values are compared against baselines. */
public enum ThresholdType {
    /** Compare as ratio (canary/baseline). */
    RATIO,
    /** Absolute difference from baseline. */
    ABSOLUTE,
    /** Statistical deviation (standard deviations). */
    DEVIATION
}
