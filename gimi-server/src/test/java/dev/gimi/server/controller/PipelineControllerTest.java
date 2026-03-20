package dev.gimi.server.controller;

import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.ShellStep;
import dev.gimi.core.model.Stage;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.validator.ValidationError;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PipelineController.class)
@AutoConfigureMockMvc(addFilters = false)
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GimiEngine engine;

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
    @WithMockUser(roles = "ADMIN")
    void shouldListPipelines() throws Exception {
        when(serverConfig.getPipelineDir()).thenReturn("/nonexistent");

        mockMvc.perform(get("/api/pipelines"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldValidatePipelineYaml() throws Exception {
        String yaml = """
                version: "1"
                name: test-pipeline
                stages:
                  - name: build
                    steps:
                      - type: shell
                        name: compile
                        run: mvn package
                """;

        Pipeline pipeline = new Pipeline("1", "test-pipeline", null, null, null, null,
                List.of(new Stage("build", null, null, null, null,
                        List.of(new ShellStep("compile", "mvn package", null, null, null)),
                        null, null, null, null, null)));

        when(engine.parse(anyString())).thenReturn(pipeline);
        when(engine.validate(any(Pipeline.class))).thenReturn(List.of());

        mockMvc.perform(post("/api/pipelines/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(yaml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.pipelineName").value("test-pipeline"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldReturnValidationErrors() throws Exception {
        String yaml = "version: '1'\nname: test\nstages: []";

        Pipeline pipeline = new Pipeline("1", "test", null, null, null, null, List.of());

        when(engine.parse(anyString())).thenReturn(pipeline);
        when(engine.validate(any(Pipeline.class))).thenReturn(List.of(
                new ValidationError(null, "stages", "Pipeline must have at least one stage",
                        "Add at least one stage")
        ));

        mockMvc.perform(post("/api/pipelines/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(yaml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].message").value("Pipeline must have at least one stage"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void shouldReturnBadRequestForInvalidYaml() throws Exception {
        when(engine.parse(anyString())).thenThrow(new RuntimeException("Parse error"));

        mockMvc.perform(post("/api/pipelines/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid yaml [[["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldHandleEmptyPipelineDirectory() throws Exception {
        when(serverConfig.getPipelineDir()).thenReturn("/tmp/nonexistent-dir");

        mockMvc.perform(get("/api/pipelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnNotFoundForUnknownPipeline() throws Exception {
        when(serverConfig.getPipelineDir()).thenReturn("/nonexistent");

        mockMvc.perform(get("/api/pipelines/unknown-pipeline"))
                .andExpect(status().isNotFound());
    }
}
