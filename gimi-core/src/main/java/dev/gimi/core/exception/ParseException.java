package dev.gimi.core.exception;

/**
 * Thrown when a pipeline definition file cannot be parsed.
 */
public final class ParseException extends GimiException {

    /**
     * Constructs a new parse exception with the specified detail message and hint.
     *
     * @param message the detail message
     * @param hint    a user-facing hint for resolving the error
     */
    public ParseException(String message, String hint) {
        super(message, hint);
    }

    /**
     * Constructs a new parse exception with the specified detail message, hint, and cause.
     *
     * @param message the detail message
     * @param hint    a user-facing hint for resolving the error
     * @param cause   the underlying cause
     */
    public ParseException(String message, String hint, Throwable cause) {
        super(message, hint, cause);
    }
}
