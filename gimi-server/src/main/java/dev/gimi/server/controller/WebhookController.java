package dev.gimi.server.controller;

import dev.gimi.core.model.*;
import dev.gimi.engine.GimiEngine;
import dev.gimi.engine.orchestrator.PipelineOrchestrator;
import dev.gimi.engine.queue.JobQueue;
import dev.gimi.engine.variable.ExecutionContext;
import dev.gimi.server.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * REST controller that receives webhook payloads and triggers matching pipelines.
 *
 * <p>Supports both simple secret validation (X-Webhook-Secret) and
 * GitHub webhook signature verification (X-Hub-Signature-256 with HMAC-SHA256).
 */
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ServerConfig serverConfig;
    private final GimiEngine engine;
    private final JobQueue jobQueue;
    private final ExecutorService executor;

    public WebhookController(ServerConfig serverConfig,
                             GimiEngine engine,
                             JobQueue jobQueue) {
        this.serverConfig = serverConfig;
        this.engine = engine;
        this.jobQueue = jobQueue;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Receives a webhook payload and triggers any pipelines whose triggers match the event.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String githubSignature,
            @RequestBody String rawBody) {

        // Validate webhook secret — always required when configured
        String webhookSecret = serverConfig.getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            // Try GitHub signature verification first
            if (githubSignature != null && !githubSignature.isBlank()) {
                if (!verifyGitHubSignature(rawBody, githubSignature, webhookSecret)) {
                    log.warn("Webhook rejected: invalid GitHub signature from {}", extractClientIp(null));
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid GitHub webhook signature"));
                }
            } else if (secret == null || !constantTimeEquals(secret, webhookSecret)) {
                log.warn("Webhook rejected: invalid or missing secret");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or missing webhook secret"));
            }
        } else {
            log.warn("Webhook secret not configured — accepting unsigned payload. Configure GIMI_SERVER_WEBHOOK_SECRET for production.");
        }

        // Parse the JSON payload
        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawBody, Map.class);
            payload = parsed;
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid JSON payload"));
        }

        // Parse event details from the payload
        String eventType = Objects.toString(payload.get("event"), "push");
        String branch = Objects.toString(payload.get("branch"), "main");
        String commitSha = Objects.toString(payload.getOrDefault("commit_sha", ""), "");

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
                triggerPipeline(pipeline, eventType, branch, commitSha);
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

    private void triggerPipeline(Pipeline pipeline, String eventType, String branch, String commitSha) {
        Map<String, String> builtins = new HashMap<>();
        builtins.put("GIMI_TRIGGER", "webhook");
        builtins.put("GIMI_EVENT", eventType);
        builtins.put("GIMI_BRANCH", branch);
        if (!commitSha.isBlank()) {
            builtins.put("GIMI_GIT_SHA", commitSha);
        }

        ExecutionContext ctx = new ExecutionContext(
                pipeline.variables(), Map.of(), builtins, null, false);

        if (serverConfig.isDistributedMode()) {
            String runId = UUID.randomUUID().toString().substring(0, 8);
            for (Stage stage : pipeline.stages()) {
                Job job = new Job(
                        UUID.randomUUID().toString().substring(0, 8),
                        pipeline.name(),
                        runId,
                        stage.name(),
                        JobStatus.QUEUED,
                        null,
                        null,
                        pipeline.variables(),
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
            log.info("Enqueued {} jobs for pipeline '{}' (run {})", pipeline.stages().size(), pipeline.name(), runId);
        } else {
            executor.submit(() -> {
                try {
                    engine.execute(pipeline, ctx);
                    log.info("Pipeline '{}' triggered by webhook completed successfully", pipeline.name());
                } catch (Exception e) {
                    log.error("Pipeline '{}' execution failed: {}", pipeline.name(), e.getMessage(), e);
                }
            });
        }
    }

    private boolean verifyGitHubSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + bytesToHex(hash);
            return expected.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify GitHub signature: {}", e.getMessage());
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

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
                            pipelines.add(engine.parseFile(p));
                        } catch (Exception e) {
                            log.warn("Failed to parse pipeline file {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan pipeline directory: {}", e.getMessage());
        }
        return pipelines;
    }

    private boolean matchesTrigger(Pipeline pipeline, String eventType, String branch,
                                   List<String> changedPaths) {
        for (Trigger trigger : pipeline.triggers()) {
            if (trigger instanceof GitTrigger git) {
                GitEvent event = parseGitEvent(eventType);
                if (event != null && !git.events().isEmpty() && !git.events().contains(event)) {
                    continue;
                }

                if (!git.branches().isEmpty() && !git.branches().contains(branch)) {
                    boolean matched = git.branches().stream()
                            .anyMatch(pattern -> matchesGlob(branch, pattern));
                    if (!matched) {
                        continue;
                    }
                }

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
            if (trigger instanceof WebhookTrigger) {
                return true;
            }
        }
        return false;
    }

    private GitEvent parseGitEvent(String eventType) {
        return switch (eventType.toLowerCase(Locale.ROOT)) {
            case "push" -> GitEvent.PUSH;
            case "pull_request" -> GitEvent.PULL_REQUEST;
            case "tag" -> GitEvent.TAG;
            default -> null;
        };
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        // Placeholder — actual request is not directly available in this context
        return "unknown";
    }

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
}
