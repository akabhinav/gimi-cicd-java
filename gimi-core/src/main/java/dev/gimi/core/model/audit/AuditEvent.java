package dev.gimi.core.model.audit;

import dev.gimi.core.model.tenant.ResourceScope;
import java.time.Instant;
import java.util.*;

public record AuditEvent(
    String id,
    String action,
    AuditCategory category,
    String resourceType,
    String resourceId,
    String userId,
    String username,
    ResourceScope scope,
    Map<String, Object> metadata,
    Map<String, Object> before,
    Map<String, Object> after,
    String ipAddress,
    String userAgent,
    Instant timestamp
) {
    public AuditEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(action);
        Objects.requireNonNull(category);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
