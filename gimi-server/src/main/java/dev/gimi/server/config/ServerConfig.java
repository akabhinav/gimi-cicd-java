package dev.gimi.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for gimi-server, bound under the {@code gimi.server} prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "gimi.server")
public class ServerConfig {

    /**
     * The directory containing pipeline YAML definitions. Defaults to the current directory.
     */
    private String pipelineDir = ".";

    /**
     * An optional shared secret used to validate incoming webhook requests.
     * When set, the server rejects requests whose {@code X-Webhook-Secret} header
     * does not match this value.
     */
    private String webhookSecret;

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
}
