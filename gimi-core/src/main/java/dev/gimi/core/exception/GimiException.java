package dev.gimi.core.exception;

/**
 * Base exception for all Gimi-specific errors.
 *
 * <p>This is a sealed class hierarchy that restricts permitted subtypes to
 * {@link ParseException}, {@link ValidationException}, and {@link ExecutionException}.
 */
public abstract sealed class GimiException extends RuntimeException
        permits ParseException, ValidationException, ExecutionException {

    private final String hint;

    /**
     * Constructs a new Gimi exception with the specified detail message and hint.
     *
     * @param message the detail message
     * @param hint    a user-facing hint for resolving the error
     */
    protected GimiException(String message, String hint) {
        super(message);
        this.hint = hint;
    }

    /**
     * Constructs a new Gimi exception with the specified detail message, hint, and cause.
     *
     * @param message the detail message
     * @param hint    a user-facing hint for resolving the error
     * @param cause   the underlying cause
     */
    protected GimiException(String message, String hint, Throwable cause) {
        super(message, cause);
        this.hint = hint;
    }

    /**
     * Returns a user-facing hint that may help resolve the error.
     *
     * @return the hint string
     */
    public String hint() {
        return hint;
    }
}
