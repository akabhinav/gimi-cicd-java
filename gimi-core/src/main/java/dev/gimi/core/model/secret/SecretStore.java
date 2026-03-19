package dev.gimi.core.model.secret;

import java.time.Instant;
import java.util.*;

public record SecretStore(
    String id,
    String name,
    SecretStoreType type,
    Map<String, String> config,
    boolean isDefault,
    Instant createdAt
) {
    public SecretStore {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        config = config == null ? Map.of() : Map.copyOf(config);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
