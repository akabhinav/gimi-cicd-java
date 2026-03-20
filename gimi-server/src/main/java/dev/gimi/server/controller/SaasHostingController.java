package dev.gimi.server.controller;

import dev.gimi.core.model.saas.*;
import dev.gimi.engine.saas.SaasHostingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for SaaS Hosting management.
 * Provides tenant provisioning, plan management, usage metering,
 * billing, build environments, and health monitoring.
 */
@RestController
@RequestMapping("/api/saas")
public class SaasHostingController {

    private final SaasHostingService saasService = new SaasHostingService();

    // ── Tenant Management ───────────────────────────────────────────────

    @PostMapping("/tenants")
    public ResponseEntity<SaasTenant> provisionTenant(@RequestBody Map<String, Object> body) {
        SaasPlan plan = parseEnum(body.get("plan"), SaasPlan.class, SaasPlan.FREE);
        SaasRegion region = parseEnum(body.get("region"), SaasRegion.class, SaasRegion.US_EAST_1);

        SaasTenant tenant = saasService.provisionTenant(
                (String) body.get("name"),
                (String) body.get("email"),
                plan, region
        );
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<SaasTenant>> listTenants(
            @RequestParam(required = false) String plan) {
        if (plan != null) {
            SaasPlan p = parseEnum(plan, SaasPlan.class, null);
            if (p != null) return ResponseEntity.ok(saasService.listTenantsByPlan(p));
        }
        return ResponseEntity.ok(saasService.listTenants());
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<SaasTenant> getTenant(@PathVariable String id) {
        return saasService.getTenant(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tenants/{id}/upgrade")
    public ResponseEntity<SaasTenant> upgradePlan(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        SaasPlan newPlan = parseEnum(body.get("plan"), SaasPlan.class, null);
        if (newPlan == null) {
            return ResponseEntity.badRequest().build();
        }
        return saasService.upgradePlan(id, newPlan)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tenants/{id}/suspend")
    public ResponseEntity<Void> suspendTenant(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        return saasService.suspendTenant(id, body.getOrDefault("reason", "manual"))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<Void> deactivateTenant(@PathVariable String id) {
        return saasService.deactivateTenant(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ── Build Environments ──────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/environments")
    public ResponseEntity<List<SaasBuildEnvironment>> getBuildEnvironments(
            @PathVariable String tenantId) {
        return ResponseEntity.ok(saasService.getBuildEnvironments(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/environments")
    public ResponseEntity<SaasBuildEnvironment> addBuildEnvironment(
            @PathVariable String tenantId,
            @RequestBody SaasBuildEnvironment env) {
        return saasService.addBuildEnvironment(tenantId, env)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Usage & Metering ────────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/usage")
    public ResponseEntity<SaasUsage> getUsage(@PathVariable String tenantId) {
        return ResponseEntity.ok(saasService.getUsage(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/usage/build")
    public ResponseEntity<SaasUsage> recordBuild(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> body) {
        long minutes = ((Number) body.getOrDefault("buildMinutes", 1)).longValue();
        return ResponseEntity.ok(saasService.recordBuild(tenantId, minutes));
    }

    @GetMapping("/tenants/{tenantId}/quota/check")
    public ResponseEntity<Map<String, Boolean>> checkQuota(@PathVariable String tenantId) {
        return ResponseEntity.ok(Map.of("withinQuota", saasService.checkQuota(tenantId)));
    }

    // ── Billing ─────────────────────────────────────────────────────────

    @PostMapping("/tenants/{tenantId}/invoices")
    public ResponseEntity<SaasInvoice> generateInvoice(
            @PathVariable String tenantId,
            @RequestBody Map<String, String> body) {
        try {
            String period = body.getOrDefault("billingPeriod", "2026-03");
            return ResponseEntity.ok(saasService.generateInvoice(tenantId, period));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tenants/{tenantId}/invoices")
    public ResponseEntity<List<SaasInvoice>> getInvoices(@PathVariable String tenantId) {
        return ResponseEntity.ok(saasService.getInvoices(tenantId));
    }

    // ── Health Monitoring ───────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/health")
    public ResponseEntity<SaasHealthStatus> getHealthStatus(@PathVariable String tenantId) {
        return ResponseEntity.ok(saasService.getHealthStatus(tenantId));
    }

    // ── Platform Status (Public) ────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getPlatformStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "operational",
                "version", "0.1.0",
                "regions", List.of(
                        Map.of("region", "us-east-1", "status", "operational"),
                        Map.of("region", "eu-west-1", "status", "operational"),
                        Map.of("region", "ap-south-1", "status", "operational")
                ),
                "activeTenants", saasService.listTenants().size(),
                "uptime", "99.97%"
        ));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> getPlans() {
        return ResponseEntity.ok(List.of(
                planInfo(SaasPlan.FREE),
                planInfo(SaasPlan.TEAM),
                planInfo(SaasPlan.PROFESSIONAL),
                planInfo(SaasPlan.ENTERPRISE)
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> planInfo(SaasPlan plan) {
        SaasResourceQuota quota = SaasResourceQuota.forPlan(plan);
        return Map.of(
                "name", plan.name(),
                "pricePerMonth", plan.pricePerMonth(),
                "maxUsers", plan.isUnlimited() ? "Unlimited" : String.valueOf(plan.maxUsers()),
                "maxBuilds", plan.isUnlimited() ? "Unlimited" : String.valueOf(plan.maxBuildsPerMonth()),
                "maxPipelines", plan.isUnlimited() ? "Unlimited" : String.valueOf(plan.maxPipelines()),
                "maxConcurrentBuilds", plan.maxConcurrentBuilds(),
                "ssoAllowed", quota.ssoAllowed(),
                "dedicatedWorkers", quota.dedicatedWorkersAllowed(),
                "prioritySupport", quota.prioritySupport()
        );
    }

    private <E extends Enum<E>> E parseEnum(Object value, Class<E> enumClass, E defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(enumClass, value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
