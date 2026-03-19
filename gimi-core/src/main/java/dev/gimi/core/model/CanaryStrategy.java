package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A canary deployment strategy that gradually rolls out changes to a subset of users.
 *
 * @param increments       the list of percentage increments for canary rollout
 * @param interval         the time interval between each increment
 * @param validation       the shell step used to validate each canary increment
 * @param rollbackOnFailure whether to automatically roll back on validation failure
 */
public record CanaryStrategy(
        List<Integer> increments,
        String interval,
        ShellStep validation,
        @JsonProperty("rollback_on_failure") boolean rollbackOnFailure
) implements DeployStrategy {

    /**
     * Creates a {@code CanaryStrategy} with a defensive copy of the increments list.
     */
    public CanaryStrategy {
        increments = increments == null ? List.of() : List.copyOf(increments);
    }
}
