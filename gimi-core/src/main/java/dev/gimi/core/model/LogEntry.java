package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * A single log line from a pipeline execution.
 */
public record LogEntry(
    @JsonProperty("run_id") String runId,
    @JsonProperty("stage_name") String stageName,
    @JsonProperty("step_name") String stepName,
    LogLevel level,
    String message,
    Instant timestamp,
    @JsonProperty("worker_id") String workerId
) {
    public LogEntry {
        level = level == null ? LogLevel.INFO : level;
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
