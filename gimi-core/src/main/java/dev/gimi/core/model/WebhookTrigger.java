package dev.gimi.core.model;

/**
 * A trigger that fires when an external webhook is received.
 *
 * @param secret the shared secret used to validate incoming webhook requests
 */
public record WebhookTrigger(
        String secret
) implements Trigger {
}
