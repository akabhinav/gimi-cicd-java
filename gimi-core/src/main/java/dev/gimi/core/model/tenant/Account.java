package dev.gimi.core.model.tenant;

import java.time.Instant;
import java.util.*;

public record Account(
    String id,
    String name,
    String description,
    AccountPlan plan,
    Map<String, String> settings,
    boolean enabled,
    Instant createdAt
) {
    public Account {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        plan = plan == null ? AccountPlan.FREE : plan;
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
