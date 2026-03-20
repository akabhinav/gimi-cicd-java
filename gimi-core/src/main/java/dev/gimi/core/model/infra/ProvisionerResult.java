package dev.gimi.core.model.infra;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of an infrastructure provisioner step execution.
 *
 * @param stepName          the step name
 * @param provisioner       provisioner type used
 * @param action            action performed
 * @param success           whether the action succeeded
 * @param resourcesCreated  number of resources created
 * @param resourcesUpdated  number of resources updated
 * @param resourcesDeleted  number of resources deleted
 * @param outputs           provisioner outputs (e.g., Terraform outputs)
 * @param planSummary       human-readable plan summary
 * @param estimatedMonthlyCost estimated monthly cost (if cost estimation enabled)
 * @param driftDetected     whether infrastructure drift was detected
 * @param driftResources    resources with detected drift
 * @param executedAt        execution timestamp
 * @param durationMs        execution duration in milliseconds
 */
public record ProvisionerResult(
        String stepName,
        ProvisionerType provisioner,
        ProvisionerAction action,
        boolean success,
        int resourcesCreated,
        int resourcesUpdated,
        int resourcesDeleted,
        Map<String, String> outputs,
        String planSummary,
        String estimatedMonthlyCost,
        boolean driftDetected,
        List<String> driftResources,
        Instant executedAt,
        long durationMs
) {
    public ProvisionerResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        driftResources = driftResources == null ? List.of() : List.copyOf(driftResources);
    }
}
