package dev.gimi.core.model.verification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A log query used during continuous verification for anomaly detection.
 *
 * @param name        query display name
 * @param query       the log search query expression
 * @param serviceId   the service identifier for log correlation
 * @param clusterBy   field to cluster similar log messages
 */
public record LogQuery(
        String name,
        String query,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("cluster_by") String clusterBy
) {}
