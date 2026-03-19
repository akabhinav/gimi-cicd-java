package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Configuration for stage approval, including notification channels and a custom message.
 *
 * @param channels the list of notification channels to alert for approval
 * @param message  the approval request message
 */
public record ApprovalConfig(
        @JsonProperty("notify") List<NotifyChannel> channels,
        String message
) {

    /**
     * Creates an {@code ApprovalConfig} with a defensive copy of the channels list.
     */
    public ApprovalConfig {
        channels = channels == null ? List.of() : List.copyOf(channels);
    }
}
