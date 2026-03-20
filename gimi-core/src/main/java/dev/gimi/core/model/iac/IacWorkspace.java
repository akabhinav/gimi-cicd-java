package dev.gimi.core.model.iac;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An IaC workspace represents a managed infrastructure configuration.
 * Similar to Harness IaCM workspaces — tracks Terraform/Pulumi/CDK state,
 * drift, cost, and approval gates.
 */
public record IacWorkspace(
        String id,
        String name,
        String description,
        String projectId,
        IacProviderType provider,
        String repository,
        String branch,
        String rootPath,
        Map<String, String> variables,
        Map<String, String> backendConfig,
        IacWorkspaceStatus status,
        String lastCommitSha,
        String connectorRef,
        boolean autoApprove,
        boolean driftDetectionEnabled,
        boolean costEstimationEnabled,
        boolean policyEnforcementEnabled,
        String approverRole,
        Instant createdAt,
        Instant updatedAt
) {
    public IacWorkspace {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        provider = provider == null ? IacProviderType.TERRAFORM : provider;
        status = status == null ? IacWorkspaceStatus.INACTIVE : status;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        backendConfig = backendConfig == null ? Map.of() : Map.copyOf(backendConfig);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
