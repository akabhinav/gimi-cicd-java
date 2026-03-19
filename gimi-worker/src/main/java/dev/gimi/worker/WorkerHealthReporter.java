package dev.gimi.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.gimi.core.model.WorkerNode;
import dev.gimi.core.model.WorkerStatus;
import dev.gimi.engine.queue.JobQueue;
import dev.gimi.worker.config.WorkerConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Reports worker health to the job queue and the central CI/CD server.
 *
 * <p>On startup, registers the worker with the server via HTTP. Periodically
 * sends heartbeats to the job queue. On shutdown, transitions through DRAINING
 * to OFFLINE, waiting for active jobs to complete.
 */
@Component
public class WorkerHealthReporter {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerHealthReporter.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(60);

    private final WorkerConfig config;
    private final JobQueue jobQueue;
    private final JobPoller jobPoller;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WorkerHealthReporter(WorkerConfig config, JobQueue jobQueue, JobPoller jobPoller) {
        this.config = config;
        this.jobQueue = jobQueue;
        this.jobPoller = jobPoller;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Registers this worker with the central server on startup.
     */
    @PostConstruct
    public void register() {
        WorkerNode node = buildWorkerNode(WorkerStatus.ONLINE);
        LOG.info("Registering worker: id={}, hostname={}, port={}, labels={}",
                node.id(), node.hostname(), node.port(), node.labels());

        try {
            String json = objectMapper.writeValueAsString(node);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/workers/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.info("Worker registered successfully with server");
            } else {
                LOG.warn("Worker registration returned status {}: {}",
                        response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.warn("Failed to register worker with server at {}: {}. " +
                     "Worker will continue operating and retry on next heartbeat.",
                    config.getServerUrl(), e.getMessage());
        }
    }

    /**
     * Sends periodic heartbeats to the job queue indicating this worker is alive.
     * Also reports the current worker status to the server.
     */
    @Scheduled(fixedDelayString = "${gimi.worker.heartbeat-interval-ms:5000}")
    public void sendHeartbeat() {
        try {
            WorkerStatus status = determineStatus();
            WorkerNode node = buildWorkerNode(status);

            String json = objectMapper.writeValueAsString(node);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/workers/heartbeat"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 300) {
                            LOG.debug("Heartbeat response status: {}", response.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        LOG.debug("Heartbeat to server failed: {}", e.getMessage());
                        return null;
                    });

            LOG.debug("Heartbeat sent: status={}, activeJobs={}/{}",
                    status, jobPoller.getActiveJobCount(), config.getMaxConcurrentJobs());

        } catch (Exception e) {
            LOG.warn("Failed to send heartbeat: {}", e.getMessage());
        }
    }

    /**
     * Handles graceful shutdown by transitioning to DRAINING, waiting for
     * active jobs to complete, then transitioning to OFFLINE.
     */
    @PreDestroy
    public void shutdown() {
        LOG.info("Initiating graceful shutdown of worker {}", config.getWorkerId());
        jobPoller.setDraining(true);

        reportStatus(WorkerStatus.DRAINING);

        try {
            boolean completed = jobPoller.shutdown(SHUTDOWN_TIMEOUT.toSeconds());
            if (completed) {
                LOG.info("All active jobs completed during shutdown");
            } else {
                LOG.warn("Shutdown timed out after {} seconds with {} active jobs remaining",
                        SHUTDOWN_TIMEOUT.toSeconds(), jobPoller.getActiveJobCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Shutdown interrupted with {} active jobs", jobPoller.getActiveJobCount());
        }

        reportStatus(WorkerStatus.OFFLINE);
        LOG.info("Worker {} is now OFFLINE", config.getWorkerId());
    }

    /**
     * Determines the current worker status based on active jobs and capacity.
     *
     * @return the current {@link WorkerStatus}
     */
    WorkerStatus determineStatus() {
        if (jobPoller.isDraining()) {
            return WorkerStatus.DRAINING;
        }
        int active = jobPoller.getActiveJobCount();
        if (active >= config.getMaxConcurrentJobs()) {
            return WorkerStatus.BUSY;
        }
        return WorkerStatus.ONLINE;
    }

    private WorkerNode buildWorkerNode(WorkerStatus status) {
        return new WorkerNode(
                config.getWorkerId(),
                config.getHostname(),
                config.getPort(),
                status,
                config.getMaxConcurrentJobs(),
                jobPoller.getActiveJobCount(),
                config.getLabels(),
                Map.of(),
                Instant.now(),
                null,
                getWorkerVersion()
        );
    }

    private void reportStatus(WorkerStatus status) {
        try {
            WorkerNode node = buildWorkerNode(status);
            String json = objectMapper.writeValueAsString(node);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServerUrl() + "/api/workers/heartbeat"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            LOG.warn("Failed to report status {} to server: {}", status, e.getMessage());
        }
    }

    private String getWorkerVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
