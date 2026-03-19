package dev.gimi.core.auth.rbac;

import dev.gimi.core.auth.Role;
import dev.gimi.core.model.tenant.ResourceScope;
import java.time.Instant;
import java.util.Objects;

public record RoleBinding(
    String id,
    String userId,
    Role role,
    ResourceScope scope,
    String resourceGroupId,
    Instant createdAt,
    Instant expiresAt
) {
    public RoleBinding {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(scope);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
