package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a pending or resolved approval request for a pipeline stage.
 */
public record ApprovalRequest(
    String id,
    @JsonProperty("run_id") String runId,
    @JsonProperty("pipeline_name") String pipelineName,
    @JsonProperty("stage_name") String stageName,
    ApprovalStatus status,
    @JsonProperty("requested_at") Instant requestedAt,
    @JsonProperty("resolved_at") Instant resolvedAt,
    @JsonProperty("resolved_by") String resolvedBy,
    String comment
) {
    public ApprovalRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(stageName, "stageName must not be null");
        status = status == null ? ApprovalStatus.PENDING : status;
    }
}
