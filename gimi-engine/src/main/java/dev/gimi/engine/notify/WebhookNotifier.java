package dev.gimi.engine.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link Notifier} implementation that sends notifications via a generic HTTP POST.
 *
 * <p>The JSON payload contains both the message and any metadata key-value pairs.
 * Notifications are best-effort: errors are logged but never thrown.
 */
public class WebhookNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

    private final String url;
    private final HttpClient httpClient;

    /**
     * Creates a new webhook notifier targeting the given URL.
     *
     * @param url the HTTP endpoint to POST notifications to
     */
    public WebhookNotifier(String url) {
        this.url = url;
        this.httpClient = HttpClient.newHttpClient();
    }

    /** {@inheritDoc} */
    @Override
    public void send(String message, Map<String, String> metadata) {
        try {
            String payload = buildPayload(message, metadata);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("Webhook notification failed with status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send webhook notification", e);
        }
    }

    /**
     * Builds a JSON payload containing the message and metadata.
     */
    private static String buildPayload(String message, Map<String, String> metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"message\": ").append(jsonEscape(message));
        if (metadata != null && !metadata.isEmpty()) {
            String metaJson = metadata.entrySet().stream()
                    .map(e -> jsonEscape(e.getKey()) + ": " + jsonEscape(e.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
            sb.append(", \"metadata\": ").append(metaJson);
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Produces a JSON-encoded string value (with surrounding quotes).
     */
    private static String jsonEscape(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
