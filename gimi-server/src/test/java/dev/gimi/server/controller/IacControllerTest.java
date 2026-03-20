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

@WebMvcTest(IacController.class)
@AutoConfigureMockMvc(addFilters = false)
class IacControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServerConfig serverConfig;
    @MockBean private JwtTokenProvider tokenProvider;
    @MockBean private JwtAuthFilter jwtAuthFilter;
    @MockBean private RateLimitFilter rateLimitFilter;
    @MockBean private WorkerAuthFilter workerAuthFilter;

    // ── Workspace CRUD ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWorkspace_returnsOk() throws Exception {
        mockMvc.perform(post("/api/iac/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name": "prod-infra",
                                "provider": "TERRAFORM",
                                "repository": "https://github.com/org/infra.git",
                                "branch": "main"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("prod-infra"))
                .andExpect(jsonPath("$.provider").value("TERRAFORM"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listWorkspaces_returnsEmptyInitially() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listWorkspaces_filtersByProjectId() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces").param("projectId", "proj-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getWorkspace_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteWorkspace_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/iac/workspaces/unknown"))
                .andExpect(status().isNotFound());
    }

    // ── Run Execution ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void executeRun_unknownWorkspace_returns404() throws Exception {
        mockMvc.perform(post("/api/iac/workspaces/unknown/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"action": "PLAN", "triggeredBy": "test"}
                            """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRunHistory_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces/ws-1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLatestRun_noRuns_returns404() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces/ws-1/runs/latest"))
                .andExpect(status().isNotFound());
    }

    // ── Drift Detection ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void detectDrift_returnsReport() throws Exception {
        mockMvc.perform(post("/api/iac/workspaces/ws-1/drift"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value("ws-1"))
                .andExpect(jsonPath("$.driftDetected").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLastDriftReport_noPrior_returns404() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces/ws-1/drift"))
                .andExpect(status().isNotFound());
    }

    // ── State Management ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void captureStateSnapshot_unknownWorkspace_returns404() throws Exception {
        mockMvc.perform(post("/api/iac/workspaces/unknown/state/snapshot"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStateHistory_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/iac/workspaces/ws-1/state/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rollbackState_unknownSnapshot_returns404() throws Exception {
        mockMvc.perform(post("/api/iac/workspaces/ws-1/state/rollback/unknown"))
                .andExpect(status().isNotFound());
    }

    // ── Full E2E Flow ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void fullWorkflow_createPlanApply() throws Exception {
        // Create workspace
        String wsResponse = mockMvc.perform(post("/api/iac/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "name": "e2e-test",
                                "provider": "TERRAFORM",
                                "repository": "https://github.com/org/infra.git",
                                "autoApprove": true,
                                "costEstimationEnabled": true,
                                "policyEnforcementEnabled": true
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("e2e-test"))
                .andReturn().getResponse().getContentAsString();

        // Extract workspace ID
        String wsId = com.fasterxml.jackson.databind.ObjectMapper
                .class.getDeclaredConstructor().newInstance()
                .readTree(wsResponse).get("id").asText();

        // Get workspace
        mockMvc.perform(get("/api/iac/workspaces/" + wsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("e2e-test"));

        // Execute plan
        mockMvc.perform(post("/api/iac/workspaces/" + wsId + "/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"action": "PLAN", "autoApprove": true, "triggeredBy": "test"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("PLAN"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resourceChanges").isArray());

        // Execute apply
        mockMvc.perform(post("/api/iac/workspaces/" + wsId + "/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"action": "APPLY", "autoApprove": true, "triggeredBy": "test"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.outputs").isMap());

        // Check state history
        mockMvc.perform(get("/api/iac/workspaces/" + wsId + "/state/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Check run history
        mockMvc.perform(get("/api/iac/workspaces/" + wsId + "/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Get latest run
        mockMvc.perform(get("/api/iac/workspaces/" + wsId + "/runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPLY"));

        // Delete workspace
        mockMvc.perform(delete("/api/iac/workspaces/" + wsId))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/iac/workspaces/" + wsId))
                .andExpect(status().isNotFound());
    }
}
