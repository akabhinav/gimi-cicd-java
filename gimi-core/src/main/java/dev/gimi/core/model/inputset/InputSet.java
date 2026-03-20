package dev.gimi.core.model.inputset;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * An Input Set provides runtime variable overrides for pipeline executions.
 *
 * <p>Enables re-running the same pipeline with different parameters without modifying
 * the pipeline definition — critical for multi-environment deployments at scale.
 *
 * @param id             unique input set ID
 * @param name           human-readable name
 * @param pipelineName   the pipeline this input set applies to
 * @param description    description of this input set
 * @param variables      variable overrides (key → value)
 * @param secrets        secret overrides (key → secret ref)
 * @param environment    target environment override
 * @param isDefault      whether this is the default input set
 * @param createdAt      creation timestamp
 */
public record InputSet(
        String id,
        String name,
        @JsonProperty("pipeline_name") String pipelineName,
        String description,
        Map<String, String> variables,
        Map<String, String> secrets,
        String environment,
        @JsonProperty("is_default") boolean isDefault,
        Instant createdAt
) {
    public InputSet {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }
}
