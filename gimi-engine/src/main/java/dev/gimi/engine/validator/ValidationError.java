package dev.gimi.engine.validator;

/**
 * Represents a single validation error found during pipeline validation.
 *
 * @param stage   the name of the stage where the error occurred, or {@code null} for pipeline-level errors
 * @param field   the field or property that failed validation
 * @param message a description of the validation failure
 * @param hint    a user-facing suggestion for how to fix the error
 */
public record ValidationError(
        String stage,
        String field,
        String message,
        String hint
) {
}
