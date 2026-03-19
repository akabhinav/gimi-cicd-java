package dev.gimi.engine.secret;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.model.SecretRef;

/**
 * Resolves secrets from system environment variables.
 *
 * <p>The {@link SecretRef#path()} is interpreted as the name of the environment
 * variable to read.
 */
public final class EnvSecretResolver implements SecretResolver {

    /**
     * Resolves the secret by reading the environment variable specified by the
     * reference's path.
     *
     * @param ref the secret reference whose path is the environment variable name
     * @return the value of the environment variable
     * @throws ExecutionException if the environment variable is not set
     */
    @Override
    public String resolve(SecretRef ref) {
        String value = System.getenv(ref.path());
        if (value == null) {
            throw new ExecutionException(
                    "Secret environment variable '" + ref.path() + "' is not set",
                    "Set the environment variable before running the pipeline: export " + ref.path() + "=<value>"
            );
        }
        return value;
    }
}
