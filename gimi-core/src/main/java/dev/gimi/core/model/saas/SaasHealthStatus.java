package dev.gimi.core.model.saas;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Health and status of a SaaS tenant's hosted infrastructure.
 */
public record SaasHealthStatus(
        String tenantId,
        OverallStatus status,
        Map<String, ComponentHealth> components,
        double uptimePercent,
        List<SaasIncident> activeIncidents,
        Instant checkedAt
) {
    public SaasHealthStatus {
        components = components == null ? Map.of() : Map.copyOf(components);
        activeIncidents = activeIncidents == null ? List.of() : List.copyOf(activeIncidents);
    }

    public enum OverallStatus {
        HEALTHY,
        DEGRADED,
        OUTAGE,
        MAINTENANCE
    }

    public record ComponentHealth(
            String name,
            OverallStatus status,
            double latencyMs,
            String message
    ) {}

    public record SaasIncident(
            String id,
            String title,
            String severity,
            String description,
            Instant startedAt,
            Instant resolvedAt
    ) {}
}
