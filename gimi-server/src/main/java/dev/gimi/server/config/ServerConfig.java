package dev.gimi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for gimi-server, bound under the {@code gimi.server} prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "gimi.server")
public class ServerConfig {

    /** The directory containing pipeline YAML definitions. */
    private String pipelineDir = "./pipelines";

    /** Shared secret used to validate incoming webhook requests. */
    private String webhookSecret = "";

    /** Secret key used for signing JWT tokens. */
    private String jwtSecret = "change-me-in-production-min-32-chars!!";

    /** Default password for the initial admin user. */
    private String defaultAdminPassword = "admin";

    /** Whether to run in distributed mode with Redis job queue. */
    private boolean distributedMode = false;

    /** Redis connection URL for distributed mode. */
    private String redisUrl = "redis://localhost:6379";

    /** PostgreSQL JDBC connection URL. */
    private String postgresUrl = "jdbc:postgresql://localhost:5432/gimi";

    /** PostgreSQL username. */
    private String postgresUsername = "gimi";

    /** PostgreSQL password. */
    private String postgresPassword = "gimi";

    /** S3 bucket name for artifact storage. */
    private String s3Bucket = "gimi-artifacts";

    /** S3 region. */
    private String s3Region = "us-east-1";

    /** S3 endpoint (empty for AWS default). */
    private String s3Endpoint = "";

    public String getPipelineDir() {
        return pipelineDir;
    }

    public void setPipelineDir(String pipelineDir) {
        this.pipelineDir = pipelineDir;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getDefaultAdminPassword() {
        return defaultAdminPassword;
    }

    public void setDefaultAdminPassword(String defaultAdminPassword) {
        this.defaultAdminPassword = defaultAdminPassword;
    }

    public boolean isDistributedMode() {
        return distributedMode;
    }

    public void setDistributedMode(boolean distributedMode) {
        this.distributedMode = distributedMode;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    public String getPostgresUrl() {
        return postgresUrl;
    }

    public void setPostgresUrl(String postgresUrl) {
        this.postgresUrl = postgresUrl;
    }

    public String getPostgresUsername() {
        return postgresUsername;
    }

    public void setPostgresUsername(String postgresUsername) {
        this.postgresUsername = postgresUsername;
    }

    public String getPostgresPassword() {
        return postgresPassword;
    }

    public void setPostgresPassword(String postgresPassword) {
        this.postgresPassword = postgresPassword;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3Region() {
        return s3Region;
    }

    public void setS3Region(String s3Region) {
        this.s3Region = s3Region;
    }

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint;
    }
}
