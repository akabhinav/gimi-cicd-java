package dev.gimi.server.controller;

import dev.gimi.server.config.ServerConfig;
import dev.gimi.server.security.JwtAuthFilter;
import dev.gimi.server.security.JwtTokenProvider;
import dev.gimi.server.security.RateLimitFilter;
import dev.gimi.server.security.WorkerAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SaasHostingController.class)
@AutoConfigureMockMvc(addFilters = false)
class SaasHostingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServerConfig serverConfig;
    @MockBean private JwtTokenProvider tokenProvider;
    @MockBean private JwtAuthFilter jwtAuthFilter;
    @MockBean private RateLimitFilter rateLimitFilter;
    @MockBean private WorkerAuthFilter workerAuthFilter;

    // ── Platform Endpoints ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPlatformStatus_returnsOperational() throws Exception {
        mockMvc.perform(get("/api/saas/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("operational"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.regions").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPlans_returnsFourPlans() throws Exception {
        mockMvc.perform(get("/api/saas/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("FREE"))
                .andExpect(jsonPath("$[3].name").value("ENTERPRISE"));
    }

    // ── Tenant Management ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void provisionTenant_returnsActiveTenant() throws Exception {
        mockMvc.perform(post("/api/saas/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Acme Corp", "email": "admin@acme.com", "plan": "TEAM", "region": "US_EAST_1"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.plan").value("TEAM"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.kubeNamespace").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void provisionTenant_defaultsToFree() throws Exception {
        mockMvc.perform(post("/api/saas/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "Free User", "email": "free@test.com"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.region").value("US_EAST_1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listTenants_returnsAll() throws Exception {
        mockMvc.perform(get("/api/saas/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getTenant_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upgradePlan_noPlan_returns400() throws Exception {
        mockMvc.perform(post("/api/saas/tenants/unknown/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upgradePlan_unknownTenant_returns404() throws Exception {
        mockMvc.perform(post("/api/saas/tenants/unknown/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"plan": "ENTERPRISE"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void suspendTenant_unknownTenant_returns404() throws Exception {
        mockMvc.perform(post("/api/saas/tenants/unknown/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reason": "test"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateTenant_unknownTenant_returns404() throws Exception {
        mockMvc.perform(delete("/api/saas/tenants/unknown"))
                .andExpect(status().isNotFound());
    }

    // ── Build Environments ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBuildEnvironments_noTenant_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/unknown/environments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── Usage & Quota ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsage_unknownTenant_returnsZeros() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/unknown/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildsThisMonth").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void checkQuota_unknownTenant_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/unknown/quota/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinQuota").value(false));
    }

    // ── Billing ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void generateInvoice_unknownTenant_returns404() throws Exception {
        mockMvc.perform(post("/api/saas/tenants/unknown/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"billingPeriod": "2026-03"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getInvoices_unknownTenant_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/unknown/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── Health ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getHealthStatus_unknownTenant_returnsOutage() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/unknown/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTAGE"));
    }

    // ── Full E2E Flow ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void fullWorkflow_provisionUpgradeBillSuspend() throws Exception {
        // Provision tenant
        String tenantJson = mockMvc.perform(post("/api/saas/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "E2E Corp", "email": "admin@e2e.com", "plan": "TEAM", "region": "EU_WEST_1"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String tenantId = com.fasterxml.jackson.databind.ObjectMapper
                .class.getDeclaredConstructor().newInstance()
                .readTree(tenantJson).get("id").asText();

        // Get tenant
        mockMvc.perform(get("/api/saas/tenants/" + tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("E2E Corp"));

        // Check build environments (3 defaults)
        mockMvc.perform(get("/api/saas/tenants/" + tenantId + "/environments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Record builds
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/usage/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"buildMinutes": 5}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildsThisMonth").value(1));

        // Check quota
        mockMvc.perform(get("/api/saas/tenants/" + tenantId + "/quota/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinQuota").value(true));

        // Check health
        mockMvc.perform(get("/api/saas/tenants/" + tenantId + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.uptimePercent").value(99.97));

        // Upgrade plan
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"plan": "PROFESSIONAL"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.ssoEnabled").value(true));

        // Generate invoice
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"billingPeriod": "2026-03"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.totalCost").isNumber());

        // Get invoices
        mockMvc.perform(get("/api/saas/tenants/" + tenantId + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Suspend tenant
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"reason": "testing"}
                            """))
                .andExpect(status().isNoContent());

        // Verify suspended
        mockMvc.perform(get("/api/saas/tenants/" + tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Deactivate
        mockMvc.perform(delete("/api/saas/tenants/" + tenantId))
                .andExpect(status().isNoContent());
    }
}
