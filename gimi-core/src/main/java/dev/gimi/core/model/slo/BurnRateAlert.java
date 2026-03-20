package dev.gimi.core.model.slo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error budget burn rate alert configuration.
 *
 * @param name          alert name
 * @param burnRate      burn rate multiplier (e.g., 14.4x = will exhaust budget in 1 hour)
 * @param windowMinutes lookback window in minutes
 * @param notifyChannel notification channel reference
 */
public record BurnRateAlert(
        String name,
        @JsonProperty("burn_rate") double burnRate,
        @JsonProperty("window_minutes") int windowMinutes,
        @JsonProperty("notify_channel") String notifyChannel
) {}
