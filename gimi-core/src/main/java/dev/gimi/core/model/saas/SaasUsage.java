package dev.gimi.core.model.saas;

import java.time.Instant;

/**
 * Current resource usage for a SaaS tenant.
 */
public record SaasUsage(
        String tenantId,
        int activeUsers,
        int pipelineCount,
        int buildsThisMonth,
        int concurrentBuildsNow,
        long artifactStorageUsedMb,
        long totalBuildMinutesThisMonth,
        int secretsCount,
        int connectorsCount,
        double cpuUtilizationPercent,
        double memoryUtilizationPercent,
        Instant measuredAt
) {
    public boolean isOverQuota(SaasResourceQuota quota) {
        return activeUsers > quota.maxUsers()
                || pipelineCount > quota.maxPipelines()
                || buildsThisMonth > quota.maxBuildsPerMonth()
                || artifactStorageUsedMb > quota.maxArtifactStorageMb();
    }

    public double usagePercentage(SaasResourceQuota quota) {
        if (quota.maxBuildsPerMonth() == Integer.MAX_VALUE) return 0.0;
        return (double) buildsThisMonth / quota.maxBuildsPerMonth() * 100.0;
    }
}
