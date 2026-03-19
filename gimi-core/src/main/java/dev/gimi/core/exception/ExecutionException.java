package dev.gimi.core.exception;

/**
 * Thrown when a pipeline execution encounters an unrecoverable error.
 */
public final class ExecutionException extends GimiException {

    /**
     * Constructs a new execution exception with the specified detail message and hint.
     *
     * @param message the detail message
     * @param hint    a user-facing hint for resolving the error
     */
    public ExecutionException(String message, String hint) {
        super(message, hint);
    }

    /**
     * Constructs a new execution exception with the specified detail message, hint, and cause.
     *
     * @param message the detail message
     * @param hint    a user-facing hint for resolving the error
     * @param cause   the underlying cause
     */
    public ExecutionException(String message, String hint, Throwable cause) {
        super(message, hint, cause);
    }
}
