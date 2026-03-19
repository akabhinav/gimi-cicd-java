package dev.gimi.server.controller;

import dev.gimi.core.model.GitEvent;
import dev.gimi.core.model.GitTrigger;
import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.Trigger;
import dev.gimi.engine.parser.PipelineParser;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * REST controller that receives webhook payloads and triggers matching pipelines.
 *
 * <p>Accepts POST requests at {@code /webhook} with a JSON body describing a Git event.
 * If a shared secret is configured, the {@code X-Webhook-Secret} header is validated
 * before processing.
 */
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ServerConfig serverConfig;
    private final PipelineParser parser;
    private final ExecutorService executor;

    public WebhookController(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.parser = new PipelineParser();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Receives a webhook payload and triggers any pipelines whose triggers match the event.
     *
     * @param secret  the optional {@code X-Webhook-Secret} header value
     * @param payload the JSON payload describing the event
     * @return a JSON response indicating which pipelines were triggered
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody Map<String, Object> payload) {

        // Validate the webhook secret if one is configured
        if (serverConfig.getWebhookSecret() != null
                && !serverConfig.getWebhookSecret().isBlank()) {
            if (secret == null || !secret.equals(serverConfig.getWebhookSecret())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or missing webhook secret"));
            }
        }

        // Parse event details from the payload
        String eventType = Objects.toString(payload.get("event"), "push");
        String branch = Objects.toString(payload.get("branch"), "main");

        @SuppressWarnings("unchecked")
        List<String> changedPaths = payload.get("changed_paths") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();

        log.info("Received webhook: event={}, branch={}, changedPaths={}", eventType, branch, changedPaths);

        // Find and trigger matching pipelines
        List<String> triggered = new ArrayList<>();
        List<Pipeline> pipelines = loadPipelines();

        for (Pipeline pipeline : pipelines) {
            if (matchesTrigger(pipeline, eventType, branch, changedPaths)) {
                triggered.add(pipeline.name());
                executor.submit(() -> executePipeline(pipeline));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("event", eventType);
        response.put("branch", branch);
        response.put("triggered_pipelines", triggered);
        response.put("triggered_count", triggered.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Loads all pipeline YAML files from the configured pipeline directory.
     */
    private List<Pipeline> loadPipelines() {
        Path dir = Path.of(serverConfig.getPipelineDir());
        if (!Files.isDirectory(dir)) {
            log.warn("Pipeline directory does not exist: {}", dir);
            return List.of();
        }

        List<Pipeline> pipelines = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir, 2)) {
            files.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .forEach(p -> {
                        try {
                            pipelines.add(parser.parseFile(p));
                        } catch (Exception e) {
                            log.warn("Failed to parse pipeline file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan pipeline directory: {}", e.getMessage());
        }
        return pipelines;
    }

    /**
     * Checks whether the given pipeline has a trigger matching the incoming event.
     */
    private boolean matchesTrigger(Pipeline pipeline, String eventType, String branch,
                                   List<String> changedPaths) {
        for (Trigger trigger : pipeline.triggers()) {
            if (trigger instanceof GitTrigger git) {
                // Check event type
                GitEvent event = parseGitEvent(eventType);
                if (event != null && !git.events().isEmpty() && !git.events().contains(event)) {
                    continue;
                }

                // Check branch
                if (!git.branches().isEmpty() && !git.branches().contains(branch)) {
                    boolean matched = git.branches().stream()
                            .anyMatch(pattern -> matchesGlob(branch, pattern));
                    if (!matched) {
                        continue;
                    }
                }

                // Check path filters
                if (git.paths() != null && !changedPaths.isEmpty()) {
                    boolean included = git.paths().include().isEmpty()
                            || changedPaths.stream().anyMatch(cp ->
                            git.paths().include().stream().anyMatch(pat -> matchesGlob(cp, pat)));
                    boolean excluded = !git.paths().exclude().isEmpty()
                            && changedPaths.stream().allMatch(cp ->
                            git.paths().exclude().stream().anyMatch(pat -> matchesGlob(cp, pat)));
                    if (!included || excluded) {
                        continue;
                    }
                }

                return true;
            }
            // Webhook triggers always match (they are triggered by any incoming webhook)
            if (trigger instanceof dev.gimi.core.model.WebhookTrigger) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses a string event type into a {@link GitEvent} enum value.
     */
    private GitEvent parseGitEvent(String eventType) {
        return switch (eventType.toLowerCase(Locale.ROOT)) {
            case "push" -> GitEvent.PUSH;
            case "pull_request" -> GitEvent.PULL_REQUEST;
            case "tag" -> GitEvent.TAG;
            default -> null;
        };
    }

    /**
     * Simple glob-style matcher supporting {@code *} and {@code **} patterns.
     */
    private boolean matchesGlob(String value, String pattern) {
        if (pattern.equals("*") || pattern.equals("**")) {
            return true;
        }
        if (pattern.contains("*")) {
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("**", "##DOUBLESTAR##")
                    .replace("*", "[^/]*")
                    .replace("##DOUBLESTAR##", ".*");
            return value.matches(regex);
        }
        return value.equals(pattern);
    }

    /**
     * Executes a pipeline asynchronously. This is a placeholder that logs execution.
     * In a full implementation, this would delegate to the engine's execution planner.
     */
    private void executePipeline(Pipeline pipeline) {
        log.info("Triggering pipeline: {}", pipeline.name());
        try {
            // In a full implementation, this would use ExecutionPlanner and StepExecutorRegistry
            log.info("Pipeline '{}' triggered successfully with {} stage(s)",
                    pipeline.name(), pipeline.stages().size());
        } catch (Exception e) {
            log.error("Pipeline '{}' execution failed: {}", pipeline.name(), e.getMessage(), e);
        }
    }
}
