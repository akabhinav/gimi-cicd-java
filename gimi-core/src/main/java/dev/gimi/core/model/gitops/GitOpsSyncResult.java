package dev.gimi.core.model.gitops;

import java.time.Instant;
import java.util.List;

/**
 * Result of a GitOps sync operation.
 *
 * @param clusterName    target cluster name
 * @param commitSha      the Git commit SHA that was synced
 * @param status         sync status
 * @param resourcesSynced number of resources synced
 * @param driftDetected  whether drift was detected before sync
 * @param driftResources resources with detected drift
 * @param syncedAt       sync timestamp
 * @param message        status message
 */
public record GitOpsSyncResult(
        String clusterName,
        String commitSha,
        GitOpsSyncStatus status,
        int resourcesSynced,
        boolean driftDetected,
        List<String> driftResources,
        Instant syncedAt,
        String message
) {
    public GitOpsSyncResult {
        driftResources = driftResources == null ? List.of() : List.copyOf(driftResources);
    }
}
