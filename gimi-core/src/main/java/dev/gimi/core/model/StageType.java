package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enumerates the types of pipeline stages.
 */
public enum StageType {

    /** A standard execution stage. */
    @JsonProperty("standard")
    STANDARD,

    /** A stage that requires manual approval before proceeding. */
    @JsonProperty("approval")
    APPROVAL
}
