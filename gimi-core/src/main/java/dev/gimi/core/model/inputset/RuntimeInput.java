package dev.gimi.core.model.inputset;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Defines a runtime input that must be provided at pipeline execution time.
 *
 * @param name           input variable name
 * @param description    human-readable description
 * @param type           the input value type
 * @param required       whether this input is required
 * @param defaultValue   default value if not provided
 * @param allowedValues  allowed values (for ENUM type)
 * @param validation     regex validation pattern
 */
public record RuntimeInput(
        String name,
        String description,
        InputType type,
        boolean required,
        @JsonProperty("default_value") String defaultValue,
        @JsonProperty("allowed_values") List<String> allowedValues,
        String validation
) {
    public RuntimeInput {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        type = type == null ? InputType.STRING : type;
    }
}
