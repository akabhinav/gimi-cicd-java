package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a worker node in the distributed execution cluster.
 */
public record WorkerNode(
    String id,
    String hostname,
    int port,
    WorkerStatus status,
    @JsonProperty("max_concurrent_jobs") int maxConcurrentJobs,
    @JsonProperty("active_jobs") int activeJobs,
    Set<String> labels,
    Map<String, String> capabilities,
    @JsonProperty("last_heartbeat") Instant lastHeartbeat,
    @JsonProperty("registered_at") Instant registeredAt,
    String version
) {
    public WorkerNode {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(hostname, "hostname must not be null");
        status = status == null ? WorkerStatus.OFFLINE : status;
        if (maxConcurrentJobs <= 0) maxConcurrentJobs = 4;
        labels = labels == null ? Set.of() : Set.copyOf(labels);
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
    }
}
