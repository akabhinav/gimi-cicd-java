package dev.gimi.core.model.iac;

import java.time.Instant;
import java.util.List;

/**
 * Drift detection report comparing actual infrastructure state vs desired IaC state.
 */
public record IacDriftReport(
        String workspaceId,
        boolean driftDetected,
        int totalResources,
        int driftedResources,
        int missingResources,
        int unexpectedResources,
        List<IacDriftDetail> details,
        Instant detectedAt
) {
    public IacDriftReport {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record IacDriftDetail(
            String resourceAddress,
            String resourceType,
            DriftType driftType,
            String attribute,
            String expectedValue,
            String actualValue,
            String severity
    ) {}

    public enum DriftType {
        CHANGED,
        MISSING,
        UNEXPECTED,
        DESTROYED_EXTERNALLY
    }
}
