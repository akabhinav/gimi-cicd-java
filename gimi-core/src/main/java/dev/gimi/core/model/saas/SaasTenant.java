package dev.gimi.core.model.saas;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A SaaS tenant represents a hosted customer in the GIMI Cloud platform.
 * Manages isolated compute, storage, and pipeline execution environments.
 */
public record SaasTenant(
        String id,
        String name,
        String email,
        SaasPlan plan,
        SaasRegion region,
        SaasTenantStatus status,
        SaasResourceQuota quota,
        SaasUsage currentUsage,
        Map<String, String> settings,
        String customDomain,
        boolean ssoEnabled,
        boolean dedicatedInfra,
        String kubeNamespace,
        Instant createdAt,
        Instant updatedAt,
        Instant trialExpiresAt
) {
    public SaasTenant {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        plan = plan == null ? SaasPlan.FREE : plan;
        region = region == null ? SaasRegion.US_EAST_1 : region;
        status = status == null ? SaasTenantStatus.PROVISIONING : status;
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
