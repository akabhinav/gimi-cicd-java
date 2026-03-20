package dev.gimi.core.model.verification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a metrics/logs provider used in Continuous Verification.
 *
 * @param name          provider name (e.g., "prometheus", "datadog", "newrelic", "splunk")
 * @param type          provider type
 * @param connectorRef  reference to a connector for authentication
 * @param queries       metric queries to execute for health analysis
 * @param logQueries    log queries for anomaly detection
 * @param thresholds    custom threshold overrides per metric
 */
public record VerificationProvider(
        String name,
        ProviderType type,
        @JsonProperty("connector_ref") String connectorRef,
        List<MetricQuery> queries,
        @JsonProperty("log_queries") List<LogQuery> logQueries,
        Map<String, Double> thresholds
) {
    public VerificationProvider {
        queries = queries == null ? List.of() : List.copyOf(queries);
        logQueries = logQueries == null ? List.of() : List.copyOf(logQueries);
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
    }
}
