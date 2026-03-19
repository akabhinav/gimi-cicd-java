package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines the condition under which a stage should run.
 */
public enum RunCondition {

    /** Run only when all previous stages succeeded. */
    @JsonProperty("on_success")
    ON_SUCCESS,

    /** Run only when a previous stage failed. */
    @JsonProperty("on_failure")
    ON_FAILURE,

    /** Run regardless of previous stage outcomes. */
    @JsonProperty("always")
    ALWAYS
}
