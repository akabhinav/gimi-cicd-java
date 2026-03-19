package dev.gimi.core.model.connector;

import java.time.Instant;
import java.util.*;

public record Connector(
    String id,
    String name,
    String description,
    ConnectorType type,
    ConnectorCategory category,
    Map<String, String> config,
    Map<String, String> credentials,
    ConnectorStatus status,
    String lastTestMessage,
    Instant lastTestedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public Connector {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        category = category == null ? ConnectorCategory.OTHER : category;
        config = config == null ? Map.of() : Map.copyOf(config);
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
        status = status == null ? ConnectorStatus.UNKNOWN : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
