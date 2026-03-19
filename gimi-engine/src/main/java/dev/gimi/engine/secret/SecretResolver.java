package dev.gimi.engine.secret;

import dev.gimi.core.model.SecretRef;

/**
 * Resolves a secret reference to its plaintext value.
 *
 * <p>Implementations read secret values from different backends such as
 * environment variables or files on disk.
 */
public interface SecretResolver {

    /**
     * Resolves the given secret reference to its plaintext value.
     *
     * @param ref the secret reference specifying the source and path
     * @return the resolved secret value
     * @throws dev.gimi.core.exception.ExecutionException if the secret cannot be resolved
     */
    String resolve(SecretRef ref);
}
