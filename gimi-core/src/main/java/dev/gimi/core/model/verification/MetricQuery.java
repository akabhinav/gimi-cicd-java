package dev.gimi.core.model.verification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A metric query used during continuous verification health analysis.
 *
 * @param name           metric display name
 * @param query          the query expression (PromQL, Datadog query, etc.)
 * @param metricType     the type of metric being measured
 * @param riskCategory   risk category for anomaly scoring
 * @param thresholdType  how to evaluate the metric against baselines
 */
public record MetricQuery(
        String name,
        String query,
        @JsonProperty("metric_type") MetricType metricType,
        @JsonProperty("risk_category") RiskCategory riskCategory,
        @JsonProperty("threshold_type") ThresholdType thresholdType
) {
    public MetricQuery {
        metricType = metricType == null ? MetricType.PERFORMANCE : metricType;
        riskCategory = riskCategory == null ? RiskCategory.MEDIUM : riskCategory;
        thresholdType = thresholdType == null ? ThresholdType.RATIO : thresholdType;
    }
}
