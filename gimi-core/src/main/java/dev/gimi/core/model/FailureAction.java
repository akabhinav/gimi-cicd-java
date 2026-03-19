package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines the action to take when a stage fails.
 */
public enum FailureAction {

    /** Abort the entire pipeline on failure. */
    @JsonProperty("abort")
    ABORT,

    /** Retry the failed stage. */
    @JsonProperty("retry")
    RETRY,

    /** Skip the failed stage and continue. */
    @JsonProperty("skip")
    SKIP
}
