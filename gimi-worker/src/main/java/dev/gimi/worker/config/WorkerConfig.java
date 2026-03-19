package dev.gimi.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration properties for the gimi-worker node, bound from the
 * {@code gimi.worker} prefix in application configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "gimi.worker")
public class WorkerConfig {

    private String workerId;
    private String hostname;
    private int port = 8081;
    private int maxConcurrentJobs = 4;
    private Set<String> labels = Set.of("default");
    private long pollIntervalMs = 1000;
    private long heartbeatIntervalMs = 5000;
    private String redisUrl = "redis://localhost:6379";
    private String serverUrl = "http://localhost:8080";

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

    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }

    public void setMaxConcurrentJobs(int maxConcurrentJobs) {
        this.maxConcurrentJobs = maxConcurrentJobs;
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
}
