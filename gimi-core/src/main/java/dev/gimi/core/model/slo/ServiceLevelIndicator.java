package dev.gimi.core.model.slo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Service Level Indicator (SLI) measures a specific aspect of service reliability.
 *
 * @param name           SLI name
 * @param type           SLI measurement type
 * @param metricQuery    the metric query (PromQL, Datadog, etc.)
 * @param goodQuery      query for "good" events (numerator)
 * @param totalQuery     query for total events (denominator)
 * @param providerType   metrics provider type
 * @param connectorRef   connector for metrics source
 */
public record ServiceLevelIndicator(
        String name,
        SliType type,
        @JsonProperty("metric_query") String metricQuery,
        @JsonProperty("good_query") String goodQuery,
        @JsonProperty("total_query") String totalQuery,
        @JsonProperty("provider_type") String providerType,
        @JsonProperty("connector_ref") String connectorRef
) {
    public ServiceLevelIndicator {
        type = type == null ? SliType.AVAILABILITY : type;
    }
}
