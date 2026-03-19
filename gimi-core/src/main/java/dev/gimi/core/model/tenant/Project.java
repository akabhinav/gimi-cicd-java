package dev.gimi.core.model.tenant;

import java.time.Instant;
import java.util.*;

public record Project(
    String id,
    String organizationId,
    String accountId,
    String name,
    String description,
    Map<String, String> tags,
    boolean enabled,
    Instant createdAt
) {
    public Project {
        Objects.requireNonNull(id);
        Objects.requireNonNull(organizationId);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(name);
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
