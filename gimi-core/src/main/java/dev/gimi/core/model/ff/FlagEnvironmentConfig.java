package dev.gimi.core.model.ff;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-environment feature flag configuration.
 *
 * @param enabled            whether the flag is enabled in this environment
 * @param value              override value for this environment
 * @param percentageRollout  environment-specific rollout percentage
 * @param targetingRules     environment-specific targeting rules
 */
public record FlagEnvironmentConfig(
        boolean enabled,
        String value,
        @JsonProperty("percentage_rollout") int percentageRollout,
        @JsonProperty("targeting_rules") List<TargetingRule> targetingRules
) {
    public FlagEnvironmentConfig {
        targetingRules = targetingRules == null ? List.of() : List.copyOf(targetingRules);
    }
}
