package dev.gimi.core.model.saas;

/**
 * Resource quotas allocated to a SaaS tenant based on their plan.
 */
public record SaasResourceQuota(
        int maxUsers,
        int maxPipelines,
        int maxBuildsPerMonth,
        int maxConcurrentBuilds,
        int maxWorkersPerExecution,
        long maxArtifactStorageMb,
        long maxLogRetentionDays,
        long maxBuildTimeoutMinutes,
        int maxSecretsCount,
        int maxConnectorsCount,
        boolean customDomainsAllowed,
        boolean dedicatedWorkersAllowed,
        boolean ssoAllowed,
        boolean auditLogsAllowed,
        boolean prioritySupport
) {
    public static SaasResourceQuota forPlan(SaasPlan plan) {
        return switch (plan) {
            case FREE -> new SaasResourceQuota(
                    1, 5, 100, 1, 1, 500, 7, 30,
                    10, 5, false, false, false, false, false);
            case TEAM -> new SaasResourceQuota(
                    5, 25, 1000, 3, 2, 5000, 30, 60,
                    50, 20, false, false, false, true, false);
            case PROFESSIONAL -> new SaasResourceQuota(
                    25, 100, 10000, 10, 5, 50000, 90, 120,
                    200, 100, true, true, true, true, false);
            case ENTERPRISE -> new SaasResourceQuota(
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    50, 20, Long.MAX_VALUE, 365, 480,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, true, true, true, true, true);
        };
    }
}
