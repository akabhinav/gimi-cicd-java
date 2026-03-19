package dev.gimi.core.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * API key for programmatic access (workers, CI integrations).
 */
public record ApiKey(
    String id,
    String name,
    @JsonProperty("key_hash") String keyHash,
    @JsonProperty("owner_id") String ownerId,
    Set<String> scopes,
    boolean enabled,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("last_used") Instant lastUsed
) {
    public ApiKey {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(keyHash, "keyHash must not be null");
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
