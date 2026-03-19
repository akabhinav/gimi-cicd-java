package dev.gimi.server.controller;

import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;
import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.queue.JobQueue;
import dev.gimi.engine.variable.ExecutionContext;
import dev.gimi.server.config.ServerConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * REST controller for pipeline run operations.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private static final Logger log = LoggerFactory.getLogger(RunController.class);

    private final GimiEngine engine;
    private final ExecutionStore executionStore;
    private final JobQueue jobQueue;
    private final ServerConfig serverConfig;
    private final ExecutorService executor;

    public RunController(GimiEngine engine,
                         ExecutionStore executionStore,
                         JobQueue jobQueue,
                         ServerConfig serverConfig) {
        this.engine = engine;
        this.executionStore = executionStore;
        this.jobQueue = jobQueue;
        this.serverConfig = serverConfig;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Trigger a new pipeline run.
     */
    @PostMapping
    public ResponseEntity<?> triggerRun(@Valid @RequestBody TriggerRunRequest request) {
        Pipeline pipeline = findPipelineByName(request.pipelineName());
        if (pipeline == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Pipeline not found: " + request.pipelineName()));
        }

        Map<String, String> variables = request.variables() != null ? request.variables() : Map.of();
        String environment = request.environment();
        boolean dryRun = request.dryRun();

        if (serverConfig.isDistributedMode()) {
            String runId = UUID.randomUUID().toString().substring(0, 8);
            for (var stage : pipeline.stages()) {
                Job job = new Job(
                        UUID.randomUUID().toString().substring(0, 8),
                        pipeline.name(),
                        runId,
                        stage.name(),
                        JobStatus.QUEUED,
                        null,
                        null,
                        variables,
                        Map.of(),
                        0,
                        0,
                        0,
                        Instant.now(),
                        null,
                        null,
                        3600
                );
                jobQueue.enqueue(job);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("runId", runId);
            response.put("pipelineName", pipeline.name());
            response.put("status", "QUEUED");
            response.put("jobCount", pipeline.stages().size());
            return ResponseEntity.accepted().body(response);
        } else {
            ExecutionContext ctx = new ExecutionContext(
                    variables, Map.of(), Map.of(), environment, dryRun);
            String runId = UUID.randomUUID().toString().substring(0, 8);

            executor.submit(() -> {
                try {
                    engine.execute(pipeline, ctx);
                } catch (Exception e) {
                    log.error("Pipeline execution failed for '{}': {}", pipeline.name(), e.getMessage(), e);
                }
            });

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("runId", runId);
            response.put("pipelineName", pipeline.name());
            response.put("status", "RUNNING");
            response.put("dryRun", dryRun);
            return ResponseEntity.accepted().body(response);
        }
    }

    /**
     * List recent pipeline runs.
     */
    @GetMapping
    public ResponseEntity<List<ExecutionRecord>> listRuns(
            @RequestParam(defaultValue = "20") int limit) {
        List<ExecutionRecord> runs = executionStore.getRecent(Math.min(limit, 100));
        return ResponseEntity.ok(runs);
    }

    /**
     * Get details of a specific run.
     */
    @GetMapping("/{runId}")
    public ResponseEntity<?> getRun(@PathVariable String runId) {
        Optional<ExecutionRecord> run = executionStore.getRun(runId);
        if (run.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Run not found: " + runId));
        }
        return ResponseEntity.ok(run.get());
    }

    /**
     * Cancel a running pipeline.
     */
    @PostMapping("/{runId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelRun(@PathVariable String runId) {
        if (serverConfig.isDistributedMode()) {
            List<Job> jobs = jobQueue.getJobsByRun(runId);
            int cancelled = 0;
            for (Job job : jobs) {
                if (job.status() == JobStatus.QUEUED || job.status() == JobStatus.ASSIGNED) {
                    jobQueue.updateStatus(job.id(), JobStatus.CANCELLED);
                    cancelled++;
                }
            }
            return ResponseEntity.ok(Map.of(
                    "runId", runId,
                    "cancelledJobs", cancelled,
                    "status", "CANCELLED"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "runId", runId,
                    "status", "CANCEL_REQUESTED",
                    "message", "Cancellation requested for single-node run"
            ));
        }
    }

    /**
     * Get jobs for a specific run.
     */
    @GetMapping("/{runId}/jobs")
    public ResponseEntity<?> getRunJobs(@PathVariable String runId) {
        if (serverConfig.isDistributedMode()) {
            List<Job> jobs = jobQueue.getJobsByRun(runId);
            return ResponseEntity.ok(jobs);
        } else {
            Optional<ExecutionRecord> run = executionStore.getRun(runId);
            if (run.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Run not found: " + runId));
            }
            return ResponseEntity.ok(Map.of(
                    "runId", runId,
                    "stages", run.get().stages()
            ));
        }
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

    public record TriggerRunRequest(
            @NotBlank String pipelineName,
            Map<String, String> variables,
            String environment,
            boolean dryRun
    ) {}
}
