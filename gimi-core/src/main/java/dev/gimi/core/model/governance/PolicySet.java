package dev.gimi.core.model.governance;

import java.time.Instant;
import java.util.*;

public record PolicySet(
    String id,
    String name,
    String description,
    List<String> policyIds,
    boolean enabled,
    Instant createdAt
) {
    public PolicySet {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
