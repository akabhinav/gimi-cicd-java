package dev.gimi.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The root model representing an entire CI/CD pipeline definition.
 *
 * @param version      the pipeline specification version
 * @param name         the name of the pipeline
 * @param variables    the global variables available to all stages
 * @param secrets      the secret references used by the pipeline
 * @param environments the named deployment environments
 * @param triggers     the triggers that start the pipeline
 * @param stages       the ordered list of stages to execute
 */
public record Pipeline(
        String version,
        String name,
        Map<String, String> variables,
        Map<String, SecretRef> secrets,
        Map<String, Environment> environments,
        List<Trigger> triggers,
        List<Stage> stages
) {

    /**
     * Creates a {@code Pipeline} with validated required fields and defensive copies of collections.
     */
    public Pipeline {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(name, "name must not be null");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
        environments = environments == null ? Map.of() : Map.copyOf(environments);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
