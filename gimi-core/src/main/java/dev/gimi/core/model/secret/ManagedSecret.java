package dev.gimi.core.model.secret;

import java.time.Instant;
import java.util.*;

public record ManagedSecret(
    String id,
    String name,
    String description,
    SecretType secretType,
    String storeId,
    String externalPath,
    Map<String, String> metadata,
    int version,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    String createdBy
) {
    public ManagedSecret {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        secretType = secretType == null ? SecretType.TEXT : secretType;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        version = version <= 0 ? 1 : version;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
