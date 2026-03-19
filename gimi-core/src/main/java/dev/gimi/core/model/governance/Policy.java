package dev.gimi.core.model.governance;

import java.time.Instant;
import java.util.*;

public record Policy(
    String id,
    String name,
    String description,
    String rego,
    PolicyType type,
    Set<PolicyEnforcementPoint> enforcementPoints,
    PolicyAction action,
    boolean enabled,
    Map<String, String> labels,
    Instant createdAt,
    Instant updatedAt
) {
    public Policy {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(rego);
        type = type == null ? PolicyType.CUSTOM : type;
        enforcementPoints = enforcementPoints == null ? Set.of() : Set.copyOf(enforcementPoints);
        action = action == null ? PolicyAction.WARN : action;
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
