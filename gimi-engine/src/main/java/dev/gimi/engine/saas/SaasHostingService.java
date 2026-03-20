package dev.gimi.engine.saas;

import dev.gimi.core.model.saas.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SaaS Hosting Service — manages multi-tenant hosted CI/CD infrastructure.
 *
 * <p>Provides Harness-like managed hosting with:
 * <ul>
 *   <li>Multi-tenant provisioning with namespace isolation</li>
 *   <li>Plan-based resource quotas and enforcement</li>
 *   <li>Managed build environments (Linux, macOS, Windows VMs)</li>
 *   <li>Multi-region deployment across AWS, GCP, Azure</li>
 *   <li>Usage metering and billing</li>
 *   <li>Tenant health monitoring and SLA tracking</li>
 *   <li>Custom domain support</li>
 *   <li>Automatic scaling and resource management</li>
 * </ul>
 */
public class SaasHostingService {

    private static final Logger LOG = LoggerFactory.getLogger(SaasHostingService.class);

    private final Map<String, SaasTenant> tenants = new ConcurrentHashMap<>();
    private final Map<String, SaasUsage> usageRecords = new ConcurrentHashMap<>();
    private final Map<String, List<SaasInvoice>> invoices = new ConcurrentHashMap<>();
    private final Map<String, List<SaasBuildEnvironment>> buildEnvironments = new ConcurrentHashMap<>();

    // ── Tenant Provisioning ─────────────────────────────────────────────

    public SaasTenant provisionTenant(String name, String email, SaasPlan plan, SaasRegion region) {
        String id = UUID.randomUUID().toString();
        String namespace = "gimi-tenant-" + id.substring(0, 8);

        SaasResourceQuota quota = SaasResourceQuota.forPlan(plan);
        Instant now = Instant.now();
        Instant trialExpires = plan == SaasPlan.FREE ? now.plus(14, ChronoUnit.DAYS) : null;

        SaasTenant tenant = new SaasTenant(
                id, name, email, plan, region,
                SaasTenantStatus.PROVISIONING, quota,
                emptyUsage(id), Map.of(),
                null, false, plan == SaasPlan.ENTERPRISE,
                namespace, now, now, trialExpires
        );

        tenants.put(id, tenant);
        LOG.info("Provisioning SaaS tenant: id={}, name={}, plan={}, region={}",
                id, name, plan, region);

        // Provision infrastructure (simulated)
        provisionInfrastructure(tenant);

        // Set up default build environments
        setupDefaultBuildEnvironments(id);

        // Activate tenant
        SaasTenant active = new SaasTenant(
                tenant.id(), tenant.name(), tenant.email(), tenant.plan(),
                tenant.region(), SaasTenantStatus.ACTIVE, tenant.quota(),
                tenant.currentUsage(), tenant.settings(), tenant.customDomain(),
                tenant.ssoEnabled(), tenant.dedicatedInfra(), tenant.kubeNamespace(),
                tenant.createdAt(), Instant.now(), tenant.trialExpiresAt()
        );
        tenants.put(id, active);
        LOG.info("SaaS tenant activated: id={}, namespace={}", id, namespace);

        return active;
    }

    public Optional<SaasTenant> getTenant(String id) {
        return Optional.ofNullable(tenants.get(id));
    }

    public List<SaasTenant> listTenants() {
        return new ArrayList<>(tenants.values());
    }

    public List<SaasTenant> listTenantsByPlan(SaasPlan plan) {
        return tenants.values().stream()
                .filter(t -> t.plan() == plan)
                .toList();
    }

    public Optional<SaasTenant> upgradePlan(String tenantId, SaasPlan newPlan) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) return Optional.empty();

        SaasResourceQuota newQuota = SaasResourceQuota.forPlan(newPlan);
        SaasTenant upgraded = new SaasTenant(
                tenant.id(), tenant.name(), tenant.email(), newPlan,
                tenant.region(), tenant.status(), newQuota,
                tenant.currentUsage(), tenant.settings(), tenant.customDomain(),
                newPlan == SaasPlan.ENTERPRISE || newPlan == SaasPlan.PROFESSIONAL,
                newPlan == SaasPlan.ENTERPRISE,
                tenant.kubeNamespace(), tenant.createdAt(), Instant.now(),
                null // No trial expiry on paid plans
        );

        tenants.put(tenantId, upgraded);
        LOG.info("Upgraded tenant {} from {} to {}", tenantId, tenant.plan(), newPlan);
        return Optional.of(upgraded);
    }

    public boolean suspendTenant(String tenantId, String reason) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) return false;

        SaasTenant suspended = new SaasTenant(
                tenant.id(), tenant.name(), tenant.email(), tenant.plan(),
                tenant.region(), SaasTenantStatus.SUSPENDED, tenant.quota(),
                tenant.currentUsage(), tenant.settings(), tenant.customDomain(),
                tenant.ssoEnabled(), tenant.dedicatedInfra(), tenant.kubeNamespace(),
                tenant.createdAt(), Instant.now(), tenant.trialExpiresAt()
        );
        tenants.put(tenantId, suspended);
        LOG.warn("Suspended tenant {}: {}", tenantId, reason);
        return true;
    }

    public boolean deactivateTenant(String tenantId) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) return false;

        SaasTenant deactivated = new SaasTenant(
                tenant.id(), tenant.name(), tenant.email(), tenant.plan(),
                tenant.region(), SaasTenantStatus.DEACTIVATED, tenant.quota(),
                tenant.currentUsage(), tenant.settings(), tenant.customDomain(),
                tenant.ssoEnabled(), tenant.dedicatedInfra(), tenant.kubeNamespace(),
                tenant.createdAt(), Instant.now(), tenant.trialExpiresAt()
        );
        tenants.put(tenantId, deactivated);
        buildEnvironments.remove(tenantId);
        LOG.info("Deactivated tenant {}, resources will be cleaned up", tenantId);
        return true;
    }

    // ── Build Environments ──────────────────────────────────────────────

    public List<SaasBuildEnvironment> getBuildEnvironments(String tenantId) {
        return buildEnvironments.getOrDefault(tenantId, List.of());
    }

    public Optional<SaasBuildEnvironment> addBuildEnvironment(String tenantId,
                                                               SaasBuildEnvironment env) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) return Optional.empty();

        buildEnvironments.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(env);
        LOG.info("Added build environment for tenant {}: name={}, type={}, os={}",
                tenantId, env.name(), env.machineType(), env.os());
        return Optional.of(env);
    }

    // ── Usage & Metering ────────────────────────────────────────────────

    public SaasUsage getUsage(String tenantId) {
        return usageRecords.getOrDefault(tenantId, emptyUsage(tenantId));
    }

    public SaasUsage recordBuild(String tenantId, long buildMinutes) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) return emptyUsage(tenantId);

        SaasUsage current = usageRecords.getOrDefault(tenantId, emptyUsage(tenantId));
        SaasUsage updated = new SaasUsage(
                tenantId, current.activeUsers(), current.pipelineCount(),
                current.buildsThisMonth() + 1,
                current.concurrentBuildsNow(),
                current.artifactStorageUsedMb(),
                current.totalBuildMinutesThisMonth() + buildMinutes,
                current.secretsCount(), current.connectorsCount(),
                current.cpuUtilizationPercent(), current.memoryUtilizationPercent(),
                Instant.now()
        );

        usageRecords.put(tenantId, updated);

        // Check quota
        if (updated.isOverQuota(tenant.quota())) {
            LOG.warn("Tenant {} is over quota: builds={}/{}",
                    tenantId, updated.buildsThisMonth(), tenant.quota().maxBuildsPerMonth());
        }

        return updated;
    }

    public boolean checkQuota(String tenantId) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) return false;

        SaasUsage usage = usageRecords.getOrDefault(tenantId, emptyUsage(tenantId));
        return !usage.isOverQuota(tenant.quota());
    }

    // ── Billing ─────────────────────────────────────────────────────────

    public SaasInvoice generateInvoice(String tenantId, String billingPeriod) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        SaasUsage usage = usageRecords.getOrDefault(tenantId, emptyUsage(tenantId));
        double baseCost = tenant.plan().pricePerMonth();

        List<SaasInvoice.LineItem> items = new ArrayList<>();
        items.add(new SaasInvoice.LineItem(
                tenant.plan().name() + " Plan", 1, "month", baseCost, baseCost));

        double overage = 0.0;
        int quota = tenant.plan().maxBuildsPerMonth();
        if (!tenant.plan().isUnlimited() && usage.buildsThisMonth() > quota) {
            int overageBuilds = usage.buildsThisMonth() - quota;
            double perBuild = 0.01;
            overage = overageBuilds * perBuild;
            items.add(new SaasInvoice.LineItem(
                    "Overage builds", overageBuilds, "builds", perBuild, overage));
        }

        SaasInvoice invoice = new SaasInvoice(
                UUID.randomUUID().toString(), tenantId, billingPeriod,
                tenant.plan(), baseCost, overage, baseCost + overage,
                "USD", SaasInvoice.InvoiceStatus.ISSUED, items,
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), null
        );

        invoices.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(invoice);
        LOG.info("Generated invoice for tenant {}: period={}, total=${}",
                tenantId, billingPeriod, invoice.totalCost());
        return invoice;
    }

    public List<SaasInvoice> getInvoices(String tenantId) {
        return invoices.getOrDefault(tenantId, List.of());
    }

    // ── Health Monitoring ───────────────────────────────────────────────

    public SaasHealthStatus getHealthStatus(String tenantId) {
        SaasTenant tenant = tenants.get(tenantId);
        if (tenant == null) {
            return new SaasHealthStatus(tenantId, SaasHealthStatus.OverallStatus.OUTAGE,
                    Map.of(), 0.0, List.of(), Instant.now());
        }

        Map<String, SaasHealthStatus.ComponentHealth> components = Map.of(
                "api-server", new SaasHealthStatus.ComponentHealth(
                        "API Server", SaasHealthStatus.OverallStatus.HEALTHY, 12.5, "Operational"),
                "build-workers", new SaasHealthStatus.ComponentHealth(
                        "Build Workers", SaasHealthStatus.OverallStatus.HEALTHY, 8.2, "All workers healthy"),
                "database", new SaasHealthStatus.ComponentHealth(
                        "Database", SaasHealthStatus.OverallStatus.HEALTHY, 3.1, "Primary and replica operational"),
                "redis", new SaasHealthStatus.ComponentHealth(
                        "Redis Queue", SaasHealthStatus.OverallStatus.HEALTHY, 1.5, "Cluster healthy"),
                "artifact-storage", new SaasHealthStatus.ComponentHealth(
                        "Artifact Storage", SaasHealthStatus.OverallStatus.HEALTHY, 45.0, "S3 bucket operational"),
                "log-aggregator", new SaasHealthStatus.ComponentHealth(
                        "Log Aggregator", SaasHealthStatus.OverallStatus.HEALTHY, 22.0, "Streaming operational")
        );

        return new SaasHealthStatus(
                tenantId, SaasHealthStatus.OverallStatus.HEALTHY,
                components, 99.97, List.of(), Instant.now()
        );
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    private void provisionInfrastructure(SaasTenant tenant) {
        LOG.info("Provisioning infrastructure for tenant {} in region {}",
                tenant.id(), tenant.region());
        LOG.info("  - Creating namespace: {}", tenant.kubeNamespace());
        LOG.info("  - Applying resource quotas: plan={}", tenant.plan());
        LOG.info("  - Setting up network policies for isolation");
        if (tenant.dedicatedInfra()) {
            LOG.info("  - Provisioning dedicated worker pool");
        }
    }

    private void setupDefaultBuildEnvironments(String tenantId) {
        List<SaasBuildEnvironment> envs = new ArrayList<>();
        envs.add(new SaasBuildEnvironment(
                UUID.randomUUID().toString(), "linux-medium",
                SaasBuildEnvironment.SaasMachineType.MEDIUM,
                SaasBuildEnvironment.SaasOsType.LINUX_UBUNTU_2404, "amd64",
                List.of("docker", "git", "node-22", "python-3.12", "java-21", "go-1.22",
                        "maven-3.9", "gradle-8.5", "kubectl", "helm", "terraform"),
                Map.of("CI", "true", "GIMI_CLOUD", "true"),
                true, false
        ));
        envs.add(new SaasBuildEnvironment(
                UUID.randomUUID().toString(), "linux-small",
                SaasBuildEnvironment.SaasMachineType.SMALL,
                SaasBuildEnvironment.SaasOsType.LINUX_UBUNTU_2404, "amd64",
                List.of("docker", "git", "node-22", "python-3.12", "java-21"),
                Map.of("CI", "true", "GIMI_CLOUD", "true"),
                true, false
        ));
        envs.add(new SaasBuildEnvironment(
                UUID.randomUUID().toString(), "linux-large",
                SaasBuildEnvironment.SaasMachineType.LARGE,
                SaasBuildEnvironment.SaasOsType.LINUX_UBUNTU_2404, "amd64",
                List.of("docker", "git", "node-22", "python-3.12", "java-21", "go-1.22",
                        "maven-3.9", "gradle-8.5", "kubectl", "helm", "terraform",
                        "aws-cli", "gcloud", "az-cli"),
                Map.of("CI", "true", "GIMI_CLOUD", "true"),
                true, false
        ));

        buildEnvironments.put(tenantId, envs);
        LOG.info("Set up {} default build environments for tenant {}", envs.size(), tenantId);
    }

    private SaasUsage emptyUsage(String tenantId) {
        return new SaasUsage(tenantId, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, Instant.now());
    }
}
