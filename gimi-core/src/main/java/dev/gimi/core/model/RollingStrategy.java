package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A rolling deployment strategy that updates instances incrementally.
 *
 * @param maxUnavailable the maximum number of instances that can be unavailable during the update
 */
public record RollingStrategy(
        @JsonProperty("max_unavailable") int maxUnavailable
) implements DeployStrategy {
}
