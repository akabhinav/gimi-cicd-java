package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enumerates the Git events that can trigger a pipeline.
 */
public enum GitEvent {

    /** A push event to a branch. */
    @JsonProperty("push")
    PUSH,

    /** A pull request event. */
    @JsonProperty("pull_request")
    PULL_REQUEST,

    /** A tag creation event. */
    @JsonProperty("tag")
    TAG
}
