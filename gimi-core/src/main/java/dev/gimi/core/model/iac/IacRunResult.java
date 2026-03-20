package dev.gimi.core.model.iac;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of an IaC run execution including resource changes,
 * cost estimates, drift findings, and policy violations.
 */
public record IacRunResult(
        String id,
        String workspaceId,
        IacAction action,
        IacRunStatus status,
        int resourcesAdded,
        int resourcesChanged,
        int resourcesDestroyed,
        int resourcesImported,
        Map<String, String> outputs,
        String planSummary,
        String planRaw,
        IacCostEstimate costEstimate,
        IacDriftReport driftReport,
        List<IacPolicyViolation> policyViolations,
        List<IacResourceChange> resourceChanges,
        String stateVersion,
        String errorMessage,
        String triggeredBy,
        Instant startedAt,
        Instant completedAt,
        long durationMs
) {
    public IacRunResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        policyViolations = policyViolations == null ? List.of() : List.copyOf(policyViolations);
        resourceChanges = resourceChanges == null ? List.of() : List.copyOf(resourceChanges);
        status = status == null ? IacRunStatus.PENDING : status;
    }
}
