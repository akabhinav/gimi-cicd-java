package dev.gimi.core.model.iac;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of IaC state for a workspace, used for state versioning and rollback.
 */
public record IacStateSnapshot(
        String id,
        String workspaceId,
        String version,
        int resourceCount,
        long stateSizeBytes,
        String stateChecksum,
        List<String> managedResources,
        String createdBy,
        Instant createdAt
) {
    public IacStateSnapshot {
        managedResources = managedResources == null ? List.of() : List.copyOf(managedResources);
    }
}
