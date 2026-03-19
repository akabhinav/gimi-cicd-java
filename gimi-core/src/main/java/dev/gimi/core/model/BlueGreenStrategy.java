package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A blue-green deployment strategy that swaps traffic between two identical environments.
 *
 * @param swapOnSuccess whether to swap the active environment upon successful deployment
 */
public record BlueGreenStrategy(
        @JsonProperty("swap_on_success") boolean swapOnSuccess
) implements DeployStrategy {
}
