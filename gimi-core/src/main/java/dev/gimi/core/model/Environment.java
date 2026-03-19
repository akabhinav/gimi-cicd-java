package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Represents a deployment environment with its configuration.
 *
 * @param variables        the environment-specific variables
 * @param requiresApproval whether deployments to this environment require manual approval
 * @param freezeWindows    the list of freeze windows during which deployments are blocked
 */
public record Environment(
        Map<String, String> variables,
        @JsonProperty("requires_approval") boolean requiresApproval,
        @JsonProperty("freeze_windows") List<FreezeWindow> freezeWindows
) {

    /**
     * Creates an {@code Environment} with defensive copies of collections.
     */
    public Environment {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        freezeWindows = freezeWindows == null ? List.of() : List.copyOf(freezeWindows);
    }
}
