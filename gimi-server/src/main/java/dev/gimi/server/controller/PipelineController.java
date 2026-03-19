package dev.gimi.server.controller;

import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.validator.ValidationError;
import dev.gimi.server.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * REST controller for pipeline management operations.
 */
@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    private final GimiEngine engine;
    private final ServerConfig serverConfig;

    public PipelineController(GimiEngine engine, ServerConfig serverConfig) {
        this.engine = engine;
        this.serverConfig = serverConfig;
    }

    /**
     * List all pipelines from the pipeline directory.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPipelines() {
        List<Map<String, Object>> pipelines = new ArrayList<>();
        Path dir = Path.of(serverConfig.getPipelineDir());

        if (!Files.isDirectory(dir)) {
            return ResponseEntity.ok(List.of());
        }

        try (Stream<Path> files = Files.walk(dir, 2)) {
            files.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .forEach(p -> {
                        try {
                            Pipeline pipeline = engine.parseFile(p);
                            Map<String, Object> info = new LinkedHashMap<>();
                            info.put("name", pipeline.name());
                            info.put("version", pipeline.version());
                            info.put("file", p.toString());
                            info.put("stageCount", pipeline.stages().size());
                            info.put("triggerCount", pipeline.triggers().size());
                            pipelines.add(info);
                        } catch (Exception e) {
                            log.warn("Failed to parse pipeline file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan pipeline directory: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(pipelines);
    }

    /**
     * Get details of a specific pipeline by name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> getPipeline(@PathVariable String name) {
        Pipeline pipeline = findPipelineByName(name);
        if (pipeline == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Pipeline not found: " + name));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", pipeline.name());
        details.put("version", pipeline.version());
        details.put("variables", pipeline.variables());
        details.put("environments", pipeline.environments().keySet());
        details.put("triggers", pipeline.triggers().size());
        details.put("stages", pipeline.stages().stream().map(s -> {
            Map<String, Object> stageInfo = new LinkedHashMap<>();
            stageInfo.put("name", s.name());
            stageInfo.put("stepsCount", s.steps().size());
            stageInfo.put("dependsOn", s.dependsOn());
            return stageInfo;
        }).toList());

        List<ValidationError> errors = engine.validate(pipeline);
        details.put("valid", errors.isEmpty());
        if (!errors.isEmpty()) {
            details.put("validationErrors", errors);
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Validate pipeline YAML content.
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validatePipeline(@RequestBody String yaml) {
        try {
            Pipeline pipeline = engine.parse(yaml);
            List<ValidationError> errors = engine.validate(pipeline);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", errors.isEmpty());
            response.put("pipelineName", pipeline.name());
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "error", e.getMessage()));
        }
    }

    /**
     * Get the DAG visualization for a pipeline.
     */
    @GetMapping("/{name}/graph")
    public ResponseEntity<?> getPipelineGraph(@PathVariable String name) {
        Pipeline pipeline = findPipelineByName(name);
        if (pipeline == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Pipeline not found: " + name));
        }

        String graph = engine.graph(pipeline);
        List<List<String>> plan = engine.plan(pipeline);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pipelineName", pipeline.name());
        response.put("ascii", graph);
        response.put("executionPlan", plan);

        return ResponseEntity.ok(response);
    }

    private Pipeline findPipelineByName(String name) {
        Path dir = Path.of(serverConfig.getPipelineDir());
        if (!Files.isDirectory(dir)) {
            return null;
        }

        try (Stream<Path> files = Files.walk(dir, 2)) {
            return files.filter(p -> {
                        String fileName = p.getFileName().toString();
                        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
                    })
                    .map(p -> {
                        try {
                            return engine.parseFile(p);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(p -> p.name().equals(name))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.error("Failed to scan pipeline directory: {}", e.getMessage());
            return null;
        }
    }
}
