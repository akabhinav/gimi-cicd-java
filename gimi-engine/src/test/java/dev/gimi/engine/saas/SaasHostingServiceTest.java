package dev.gimi.engine.saas;

import dev.gimi.core.model.saas.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class SaasHostingServiceTest {

    private SaasHostingService service;

    @BeforeEach
    void setUp() {
        service = new SaasHostingService();
    }

    // ── Tenant Provisioning ─────────────────────────────────────────────

    @Test
    void provisionTenant_createsActiveTenant() {
        SaasTenant tenant = service.provisionTenant(
                "Acme Corp", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        assertThat(tenant.name()).isEqualTo("Acme Corp");
        assertThat(tenant.plan()).isEqualTo(SaasPlan.TEAM);
        assertThat(tenant.region()).isEqualTo(SaasRegion.US_EAST_1);
        assertThat(tenant.status()).isEqualTo(SaasTenantStatus.ACTIVE);
        assertThat(tenant.kubeNamespace()).startsWith("gimi-tenant-");
    }

    @Test
    void provisionTenant_freePlan_hasTrialExpiry() {
        SaasTenant tenant = service.provisionTenant(
                "Trial User", "trial@test.com", SaasPlan.FREE, SaasRegion.EU_WEST_1);

        assertThat(tenant.trialExpiresAt()).isNotNull();
    }

    @Test
    void provisionTenant_enterprisePlan_hasDedicatedInfra() {
        SaasTenant tenant = service.provisionTenant(
                "BigCo", "admin@bigco.com", SaasPlan.ENTERPRISE, SaasRegion.US_WEST_2);

        assertThat(tenant.dedicatedInfra()).isTrue();
    }

    @Test
    void provisionTenant_setsUpBuildEnvironments() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        List<SaasBuildEnvironment> envs = service.getBuildEnvironments(tenant.id());
        assertThat(envs).hasSize(3);
        assertThat(envs.stream().map(SaasBuildEnvironment::name))
                .containsExactlyInAnyOrder("linux-medium", "linux-small", "linux-large");
    }

    @Test
    void getTenant_existingId_returnsTenant() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.FREE, SaasRegion.US_EAST_1);

        assertThat(service.getTenant(tenant.id())).isPresent();
    }

    @Test
    void getTenant_unknownId_returnsEmpty() {
        assertThat(service.getTenant("unknown")).isEmpty();
    }

    @Test
    void listTenants_returnsAll() {
        service.provisionTenant("A", "a@a.com", SaasPlan.FREE, SaasRegion.US_EAST_1);
        service.provisionTenant("B", "b@b.com", SaasPlan.TEAM, SaasRegion.EU_WEST_1);

        assertThat(service.listTenants()).hasSize(2);
    }

    @Test
    void listTenantsByPlan_filtersCorrectly() {
        service.provisionTenant("A", "a@a.com", SaasPlan.FREE, SaasRegion.US_EAST_1);
        service.provisionTenant("B", "b@b.com", SaasPlan.TEAM, SaasRegion.EU_WEST_1);
        service.provisionTenant("C", "c@c.com", SaasPlan.TEAM, SaasRegion.US_WEST_2);

        assertThat(service.listTenantsByPlan(SaasPlan.TEAM)).hasSize(2);
        assertThat(service.listTenantsByPlan(SaasPlan.FREE)).hasSize(1);
    }

    // ── Plan Upgrade ────────────────────────────────────────────────────

    @Test
    void upgradePlan_updatesQuotaAndPlan() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.FREE, SaasRegion.US_EAST_1);

        Optional<SaasTenant> upgraded = service.upgradePlan(tenant.id(), SaasPlan.PROFESSIONAL);

        assertThat(upgraded).isPresent();
        assertThat(upgraded.get().plan()).isEqualTo(SaasPlan.PROFESSIONAL);
        assertThat(upgraded.get().ssoEnabled()).isTrue();
        assertThat(upgraded.get().trialExpiresAt()).isNull();
    }

    @Test
    void upgradePlan_unknownTenant_returnsEmpty() {
        assertThat(service.upgradePlan("unknown", SaasPlan.TEAM)).isEmpty();
    }

    // ── Suspend / Deactivate ────────────────────────────────────────────

    @Test
    void suspendTenant_setsStatusSuspended() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        assertThat(service.suspendTenant(tenant.id(), "payment overdue")).isTrue();
        assertThat(service.getTenant(tenant.id()).get().status())
                .isEqualTo(SaasTenantStatus.SUSPENDED);
    }

    @Test
    void deactivateTenant_removesEnvironments() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        assertThat(service.deactivateTenant(tenant.id())).isTrue();
        assertThat(service.getTenant(tenant.id()).get().status())
                .isEqualTo(SaasTenantStatus.DEACTIVATED);
        assertThat(service.getBuildEnvironments(tenant.id())).isEmpty();
    }

    // ── Build Environments ──────────────────────────────────────────────

    @Test
    void addBuildEnvironment_appendsToTenantEnvs() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        SaasBuildEnvironment macEnv = new SaasBuildEnvironment(
                "mac-1", "macos-large",
                SaasBuildEnvironment.SaasMachineType.LARGE,
                SaasBuildEnvironment.SaasOsType.MACOS_15, "arm64",
                List.of("xcode-16", "swift-5.9", "cocoapods"),
                java.util.Map.of("CI", "true"),
                false, false
        );

        Optional<SaasBuildEnvironment> result = service.addBuildEnvironment(tenant.id(), macEnv);
        assertThat(result).isPresent();
        assertThat(service.getBuildEnvironments(tenant.id())).hasSize(4); // 3 defaults + 1
    }

    @Test
    void addBuildEnvironment_unknownTenant_returnsEmpty() {
        SaasBuildEnvironment env = new SaasBuildEnvironment(
                "e1", "test", SaasBuildEnvironment.SaasMachineType.SMALL,
                SaasBuildEnvironment.SaasOsType.LINUX_UBUNTU_2404, "amd64",
                List.of(), java.util.Map.of(), true, false);

        assertThat(service.addBuildEnvironment("unknown", env)).isEmpty();
    }

    // ── Usage & Metering ────────────────────────────────────────────────

    @Test
    void recordBuild_incrementsBuildCount() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        service.recordBuild(tenant.id(), 5);
        service.recordBuild(tenant.id(), 10);
        SaasUsage usage = service.getUsage(tenant.id());

        assertThat(usage.buildsThisMonth()).isEqualTo(2);
        assertThat(usage.totalBuildMinutesThisMonth()).isEqualTo(15);
    }

    @Test
    void checkQuota_withinLimits_returnsTrue() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        assertThat(service.checkQuota(tenant.id())).isTrue();
    }

    @Test
    void checkQuota_unknownTenant_returnsFalse() {
        assertThat(service.checkQuota("unknown")).isFalse();
    }

    @Test
    void getUsage_unknownTenant_returnsEmpty() {
        SaasUsage usage = service.getUsage("unknown");
        assertThat(usage.buildsThisMonth()).isEqualTo(0);
    }

    // ── Billing ─────────────────────────────────────────────────────────

    @Test
    void generateInvoice_createsInvoiceWithLineItems() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        SaasInvoice invoice = service.generateInvoice(tenant.id(), "2026-03");

        assertThat(invoice.tenantId()).isEqualTo(tenant.id());
        assertThat(invoice.plan()).isEqualTo(SaasPlan.TEAM);
        assertThat(invoice.planBaseCost()).isEqualTo(29.0);
        assertThat(invoice.totalCost()).isEqualTo(29.0);
        assertThat(invoice.lineItems()).hasSize(1);
        assertThat(invoice.status()).isEqualTo(SaasInvoice.InvoiceStatus.ISSUED);
    }

    @Test
    void generateInvoice_withOverage_addsOverageLineItem() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.FREE, SaasRegion.US_EAST_1);

        // Record 150 builds (over the 100 limit for FREE)
        for (int i = 0; i < 150; i++) {
            service.recordBuild(tenant.id(), 1);
        }

        SaasInvoice invoice = service.generateInvoice(tenant.id(), "2026-03");

        assertThat(invoice.overageCost()).isGreaterThan(0);
        assertThat(invoice.lineItems()).hasSize(2); // base + overage
    }

    @Test
    void generateInvoice_unknownTenant_throws() {
        assertThatThrownBy(() -> service.generateInvoice("unknown", "2026-03"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getInvoices_returnsAllForTenant() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        service.generateInvoice(tenant.id(), "2026-01");
        service.generateInvoice(tenant.id(), "2026-02");

        assertThat(service.getInvoices(tenant.id())).hasSize(2);
    }

    // ── Health Monitoring ───────────────────────────────────────────────

    @Test
    void getHealthStatus_activeTenant_returnsHealthy() {
        SaasTenant tenant = service.provisionTenant(
                "Acme", "admin@acme.com", SaasPlan.TEAM, SaasRegion.US_EAST_1);

        SaasHealthStatus health = service.getHealthStatus(tenant.id());

        assertThat(health.status()).isEqualTo(SaasHealthStatus.OverallStatus.HEALTHY);
        assertThat(health.uptimePercent()).isEqualTo(99.97);
        assertThat(health.components()).containsKeys("api-server", "build-workers", "database", "redis");
    }

    @Test
    void getHealthStatus_unknownTenant_returnsOutage() {
        SaasHealthStatus health = service.getHealthStatus("unknown");
        assertThat(health.status()).isEqualTo(SaasHealthStatus.OverallStatus.OUTAGE);
    }

    // ── Model Tests ─────────────────────────────────────────────────────

    @Test
    void saasResourceQuota_forPlan_returnsCorrectLimits() {
        SaasResourceQuota free = SaasResourceQuota.forPlan(SaasPlan.FREE);
        assertThat(free.maxUsers()).isEqualTo(1);
        assertThat(free.ssoAllowed()).isFalse();

        SaasResourceQuota enterprise = SaasResourceQuota.forPlan(SaasPlan.ENTERPRISE);
        assertThat(enterprise.maxUsers()).isEqualTo(Integer.MAX_VALUE);
        assertThat(enterprise.ssoAllowed()).isTrue();
        assertThat(enterprise.prioritySupport()).isTrue();
    }

    @Test
    void saasUsage_isOverQuota_detectsExceedance() {
        SaasResourceQuota quota = SaasResourceQuota.forPlan(SaasPlan.FREE);
        SaasUsage overQuota = new SaasUsage(
                "t1", 5, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0,
                java.time.Instant.now());

        assertThat(overQuota.isOverQuota(quota)).isTrue(); // 5 users > 1 max
    }

    @Test
    void saasPlan_limits_areCorrect() {
        assertThat(SaasPlan.FREE.maxUsers()).isEqualTo(1);
        assertThat(SaasPlan.FREE.pricePerMonth()).isEqualTo(0);
        assertThat(SaasPlan.TEAM.maxBuildsPerMonth()).isEqualTo(1000);
        assertThat(SaasPlan.ENTERPRISE.isUnlimited()).isTrue();
    }

    @Test
    void saasRegion_hasCorrectMetadata() {
        assertThat(SaasRegion.US_EAST_1.code()).isEqualTo("us-east-1");
        assertThat(SaasRegion.US_EAST_1.cloudProvider()).isEqualTo("AWS");
        assertThat(SaasRegion.GCP_US_CENTRAL1.cloudProvider()).isEqualTo("GCP");
        assertThat(SaasRegion.AZURE_EASTUS.cloudProvider()).isEqualTo("Azure");
    }
}
