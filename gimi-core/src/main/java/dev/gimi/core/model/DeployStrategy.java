package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A sealed interface representing a deployment strategy for a pipeline stage.
 *
 * <p>Strategies are distinguished by a {@code type} discriminator property in the YAML/JSON
 * representation using kebab-case names. Permitted implementations are
 * {@link BlueGreenStrategy}, {@link CanaryStrategy}, and {@link RollingStrategy}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BlueGreenStrategy.class, name = "blue-green"),
        @JsonSubTypes.Type(value = CanaryStrategy.class, name = "canary"),
        @JsonSubTypes.Type(value = RollingStrategy.class, name = "rolling")
})
public sealed interface DeployStrategy permits BlueGreenStrategy, CanaryStrategy, RollingStrategy {
}
