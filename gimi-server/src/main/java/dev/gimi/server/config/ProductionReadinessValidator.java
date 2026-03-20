package dev.gimi.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validates production-critical configuration at startup.
 * Fails fast if insecure defaults are detected in non-dev profiles.
 */
@Component
public class ProductionReadinessValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionReadinessValidator.class);
    private static final String DEFAULT_JWT_SECRET = "change-me-in-production-min-32-chars!!";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin";

    private final ServerConfig serverConfig;
    private final Environment environment;

    public ProductionReadinessValidator(ServerConfig serverConfig, Environment environment) {
        this.serverConfig = serverConfig;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // JWT secret must be changed
        if (DEFAULT_JWT_SECRET.equals(serverConfig.getJwtSecret())) {
            String msg = "JWT secret is using the insecure default value. Set GIMI_SERVER_JWT_SECRET to a unique 32+ char secret.";
            if (isDev) {
                warnings.add(msg);
            } else {
                errors.add(msg);
            }
        } else if (serverConfig.getJwtSecret().length() < 32) {
            errors.add("JWT secret must be at least 32 characters long.");
        }

        // Default admin password must be changed
        if (DEFAULT_ADMIN_PASSWORD.equals(serverConfig.getDefaultAdminPassword())) {
            String msg = "Default admin password is 'admin'. Set GIMI_SERVER_DEFAULT_ADMIN_PASSWORD to a strong password.";
            if (isDev) {
                warnings.add(msg);
            } else {
                errors.add(msg);
            }
        }

        // Webhook secret should be configured
        if (serverConfig.getWebhookSecret() == null || serverConfig.getWebhookSecret().isBlank()) {
            warnings.add("Webhook secret is not configured. Webhook endpoints will accept unsigned payloads. Set GIMI_SERVER_WEBHOOK_SECRET.");
        }

        // Database credentials check
        if ("gimi".equals(serverConfig.getPostgresPassword()) && !isDev) {
            errors.add("PostgreSQL password is using the default value. Set GIMI_SERVER_POSTGRES_PASSWORD.");
        }

        // Report warnings
        for (String warning : warnings) {
            log.warn("SECURITY WARNING: {}", warning);
        }

        // Report errors and fail
        if (!errors.isEmpty()) {
            for (String error : errors) {
                log.error("FATAL SECURITY ERROR: {}", error);
            }
            throw new IllegalStateException(
                    "Server startup blocked due to insecure configuration. " +
                    "Fix the above errors or set spring.profiles.active=dev to run in development mode. " +
                    "Errors: " + String.join("; ", errors));
        }

        if (warnings.isEmpty() && errors.isEmpty()) {
            log.info("Production readiness validation passed.");
        }
    }
}
