package dev.gimi.core.model.slo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Service Level Objective (SLO) for reliability management integrated into pipelines.
 *
 * <p>Automatically gates deployments when SLO error budget is exhausted,
 * and tracks feature flag impact on service reliability.
 *
 * @param id                unique SLO ID
 * @param name              SLO display name
 * @param serviceName       the service this SLO applies to
 * @param environment       the environment
 * @param slis              service level indicators
 * @param targetPercentage  target SLO percentage (e.g., 99.9)
 * @param windowDays        measurement window in days (7, 28, 30, 90)
 * @param burnRateAlerts    burn rate alert thresholds
 * @param gateDeployments   whether to gate deployments when error budget exhausted
 * @param createdAt         creation timestamp
 */
public record ServiceLevelObjective(
        String id,
        String name,
        @JsonProperty("service_name") String serviceName,
        String environment,
        List<ServiceLevelIndicator> slis,
        @JsonProperty("target_percentage") double targetPercentage,
        @JsonProperty("window_days") int windowDays,
        @JsonProperty("burn_rate_alerts") List<BurnRateAlert> burnRateAlerts,
        @JsonProperty("gate_deployments") boolean gateDeployments,
        Instant createdAt
) {
    public ServiceLevelObjective {
        slis = slis == null ? List.of() : List.copyOf(slis);
        burnRateAlerts = burnRateAlerts == null ? List.of() : List.copyOf(burnRateAlerts);
        if (windowDays <= 0) windowDays = 30;
    }
}
