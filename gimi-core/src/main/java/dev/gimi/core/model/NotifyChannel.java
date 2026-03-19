package dev.gimi.core.model;

/**
 * Represents a notification channel for approval requests.
 *
 * @param channel the name of the notification channel
 * @param webhook the webhook URL for sending notifications
 * @param message the notification message template
 */
public record NotifyChannel(
        String channel,
        String webhook,
        String message
) {
}
