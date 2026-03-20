package dev.gimi.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration properties for the gimi-worker node, bound from the
 * {@code gimi.worker} prefix in application configuration.
 *
 * <p>Tuned for high-throughput 10K+ pipeline deployments:
 * <ul>
 *   <li>Default max-concurrent-jobs = CPU cores * 4 (optimal for I/O-bound pipeline work)</li>
 *   <li>Faster poll interval (500ms) with adaptive backoff in JobPoller</li>
 *   <li>Shorter heartbeat interval (3s) for faster stale-job detection</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "gimi.worker")
public class WorkerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerConfig.class);

    /** Default concurrent jobs: CPU cores * 4 (suitable for I/O-bound pipeline steps). */
    private static final int DEFAULT_MAX_CONCURRENT_JOBS =
            Math.max(8, Runtime.getRuntime().availableProcessors() * 4);

    private String workerId;
    private String hostname;
    private int port = 8081;
    private int maxConcurrentJobs = DEFAULT_MAX_CONCURRENT_JOBS;
    private Set<String> labels = Set.of("default");
    private long pollIntervalMs = 500;
    private long heartbeatIntervalMs = 3000;
    private String redisUrl = "redis://localhost:6379";
    private String serverUrl = "http://localhost:8080";
    private String workerToken = "";

    /**
     * Returns the unique worker identifier, auto-generating a UUID if not explicitly configured.
     *
     * @return the worker ID
     */
    public String getWorkerId() {
        if (workerId == null || workerId.isBlank()) {
            workerId = UUID.randomUUID().toString();
        }
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    /**
     * Returns the hostname of this worker, auto-detecting from the local host if not configured.
     *
     * @return the hostname
     */
    public String getHostname() {
        if (hostname == null || hostname.isBlank()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                hostname = "unknown";
            }
        }
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Returns the maximum number of concurrent jobs this worker can execute.
     * If set to 0 or negative, auto-detects as CPU cores * 4 (minimum 8).
     */
    public int getMaxConcurrentJobs() {
        if (maxConcurrentJobs <= 0) {
            return DEFAULT_MAX_CONCURRENT_JOBS;
        }
        return maxConcurrentJobs;
    }

    public void setMaxConcurrentJobs(int maxConcurrentJobs) {
        this.maxConcurrentJobs = maxConcurrentJobs;
        LOG.info("Worker max-concurrent-jobs set to {} (CPU cores: {})",
                maxConcurrentJobs, Runtime.getRuntime().availableProcessors());
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getWorkerToken() {
        return workerToken;
    }

    public void setWorkerToken(String workerToken) {
        this.workerToken = workerToken;
    }
}
