package dev.gimi.core.model.ff;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Feature Flag for controlled feature rollout integrated into CI/CD pipelines.
 *
 * <p>Unlike Harness which requires a separate FF module, Gimi integrates feature flags
 * natively into the pipeline engine with percentage rollouts, targeting rules, and
 * SLO impact tracking.
 *
 * @param id              unique flag identifier
 * @param name            human-readable flag name
 * @param key             flag key used in code (e.g., "enable-new-checkout")
 * @param description     flag description
 * @param type            flag value type
 * @param defaultValue    default value when flag is off
 * @param enabled         whether the flag is globally enabled
 * @param environments    per-environment overrides
 * @param targetingRules  rules for selective targeting
 * @param percentageRollout percentage of traffic that sees the flag enabled (0-100)
 * @param tags            metadata tags
 * @param createdAt       creation timestamp
 * @param updatedAt       last update timestamp
 */
public record FeatureFlag(
        String id,
        String name,
        String key,
        String description,
        FlagType type,
        String defaultValue,
        boolean enabled,
        Map<String, FlagEnvironmentConfig> environments,
        @JsonProperty("targeting_rules") List<TargetingRule> targetingRules,
        @JsonProperty("percentage_rollout") int percentageRollout,
        Map<String, String> tags,
        Instant createdAt,
        Instant updatedAt
) {
    public FeatureFlag {
        environments = environments == null ? Map.of() : Map.copyOf(environments);
        targetingRules = targetingRules == null ? List.of() : List.copyOf(targetingRules);
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
