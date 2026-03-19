package dev.gimi.core.exception;

import java.util.List;

/**
 * Thrown when pipeline validation fails due to one or more errors.
 */
public final class ValidationException extends GimiException {

    private final List<String> errors;

    /**
     * Constructs a new validation exception with the given list of validation errors.
     *
     * <p>The detail message is automatically generated from the error count, and the hint
     * directs the user to run {@code gimi validate} for further details.
     *
     * @param errors the list of validation error descriptions
     */
    public ValidationException(List<String> errors) {
        super(
                "Pipeline validation failed with " + errors.size() + " error(s)",
                "Run 'gimi validate' for details"
        );
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns an unmodifiable list of validation error descriptions.
     *
     * @return the validation errors
     */
    public List<String> errors() {
        return errors;
    }
}
