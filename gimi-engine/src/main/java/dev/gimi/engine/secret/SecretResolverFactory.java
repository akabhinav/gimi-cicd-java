package dev.gimi.engine.secret;

import dev.gimi.core.exception.ExecutionException;

/**
 * Factory for creating {@link SecretResolver} instances based on the secret
 * source type.
 */
public final class SecretResolverFactory {

    private SecretResolverFactory() {
        // prevent instantiation
    }

    /**
     * Creates a {@link SecretResolver} for the given source type.
     *
     * @param from the secret source type ({@code "env"} or {@code "file"})
     * @return a resolver appropriate for the source type
     * @throws ExecutionException if the source type is not recognized
     */
    public static SecretResolver create(String from) {
        return switch (from) {
            case "env" -> new EnvSecretResolver();
            case "file" -> new FileSecretResolver();
            default -> throw new ExecutionException(
                    "Unknown secret source type: '" + from + "'",
                    "Supported secret sources are 'env' and 'file'"
            );
        };
    }
}
