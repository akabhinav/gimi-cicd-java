package dev.gimi.core.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a user in the system.
 */
public record User(
    String id,
    String username,
    @JsonIgnore @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String passwordHash,
    String email,
    Set<Role> roles,
    boolean enabled,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("last_login") Instant lastLogin
) {
    public User {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        roles = roles == null ? Set.of(Role.VIEWER) : Set.copyOf(roles);
    }
}
