package dev.gimi.server.controller;

import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.queue.JobQueue;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RunController.class)
@AutoConfigureMockMvc(addFilters = false)
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GimiEngine engine;

    @MockBean
    private ExecutionStore executionStore;

    @MockBean
    private JobQueue jobQueue;

    @MockBean
    private ServerConfig serverConfig;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    @MockBean
    private WorkerAuthFilter workerAuthFilter;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldListRecentRuns() throws Exception {
        Instant now = Instant.now();
        ExecutionRecord record = new ExecutionRecord.Builder("run-1", "my-pipeline")
                .status(ExecutionStatus.PASSED)
                .startedAt(now)
                .finishedAt(now.plusSeconds(60))
                .build();

        when(executionStore.getRecent(anyInt())).thenReturn(List.of(record));

        mockMvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].runId").value("run-1"))
                .andExpect(jsonPath("$[0].pipelineName").value("my-pipeline"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldListRunsWithDefaultLimit() throws Exception {
        when(executionStore.getRecent(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldGetSpecificRunById() throws Exception {
        Instant now = Instant.now();
        ExecutionRecord record = new ExecutionRecord.Builder("run-42", "pipeline")
                .status(ExecutionStatus.FAILED)
                .startedAt(now)
                .finishedAt(now.plusSeconds(30))
                .trigger("manual")
                .build();

        when(executionStore.getRun("run-42")).thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/runs/run-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-42"))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldReturnNotFoundForUnknownRun() throws Exception {
        when(executionStore.getRun(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/runs/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldTriggerRunReturningNotFoundForUnknownPipeline() throws Exception {
        when(serverConfig.getPipelineDir()).thenReturn("/nonexistent");

        mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "pipelineName": "unknown-pipeline",
                                    "dryRun": false
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void shouldCancelRun() throws Exception {
        when(serverConfig.isDistributedMode()).thenReturn(false);

        mockMvc.perform(post("/api/runs/run-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldListRunsWithCustomLimit() throws Exception {
        when(executionStore.getRecent(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/runs").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
