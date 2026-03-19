package dev.gimi.engine.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * A {@link Notifier} implementation that sends messages to a Slack incoming webhook.
 *
 * <p>Notifications are best-effort: errors are logged but never thrown.
 */
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final String webhookUrl;
    private final HttpClient httpClient;

    /**
     * Creates a new Slack notifier targeting the given incoming webhook URL.
     *
     * @param webhookUrl the Slack incoming webhook URL
     */
    public SlackNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /** {@inheritDoc} */
    @Override
    public void send(String message, Map<String, String> metadata) {
        try {
            String payload = "{\"text\": " + jsonEscape(message) + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("Slack notification failed with status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send Slack notification", e);
        }
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
