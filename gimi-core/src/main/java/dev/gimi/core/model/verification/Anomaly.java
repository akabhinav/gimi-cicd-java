package dev.gimi.core.model.verification;

import java.time.Instant;

/**
 * A detected anomaly during continuous verification.
 *
 * @param id          unique anomaly ID
 * @param type        anomaly type (METRIC_SPIKE, ERROR_RATE, LATENCY, LOG_CLUSTER)
 * @param source      source provider/metric name
 * @param severity    severity level
 * @param message     human-readable description
 * @param detectedAt  when the anomaly was detected
 * @param score       anomaly score (0.0-1.0)
 */
public record Anomaly(
        String id,
        AnomalyType type,
        String source,
        RiskCategory severity,
        String message,
        Instant detectedAt,
        double score
) {}
