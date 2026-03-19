package dev.gimi.core.model;

import java.util.Objects;

/**
 * A reference to a secret value, specifying its source and path.
 *
 * @param from the secret source type, either {@code "env"} or {@code "file"}
 * @param path the path or name of the secret within the source
 */
public record SecretRef(
        String from,
        String path
) {

    /**
     * Creates a {@code SecretRef} with validated parameters.
     */
    public SecretRef {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }
}
