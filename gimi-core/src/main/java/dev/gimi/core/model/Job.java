package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a unit of work to be executed by a worker node.
 */
public record Job(
    String id,
    @JsonProperty("pipeline_name") String pipelineName,
    @JsonProperty("run_id") String runId,
    @JsonProperty("stage_name") String stageName,
    JobStatus status,
    @JsonProperty("worker_id") String workerId,
    @JsonProperty("pipeline_yaml") String pipelineYaml,
    Map<String, String> variables,
    Map<String, String> secrets,
    int priority,
    @JsonProperty("max_retries") int maxRetries,
    @JsonProperty("retry_count") int retryCount,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("finished_at") Instant finishedAt,
    @JsonProperty("timeout_seconds") long timeoutSeconds
) {
    public Job {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(stageName, "stageName must not be null");
        status = status == null ? JobStatus.QUEUED : status;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
        if (timeoutSeconds <= 0) timeoutSeconds = 3600;
    }
}
