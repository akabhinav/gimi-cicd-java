package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Reference to a build artifact stored in artifact storage.
 */
public record ArtifactRef(
    String id,
    @JsonProperty("run_id") String runId,
    @JsonProperty("stage_name") String stageName,
    @JsonProperty("step_name") String stepName,
    String path,
    @JsonProperty("content_type") String contentType,
    @JsonProperty("size_bytes") long sizeBytes,
    String checksum,
    @JsonProperty("storage_key") String storageKey,
    Map<String, String> metadata,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("expires_at") Instant expiresAt
) {
    public ArtifactRef {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
