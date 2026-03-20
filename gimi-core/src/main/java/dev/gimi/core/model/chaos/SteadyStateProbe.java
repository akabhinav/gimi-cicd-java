package dev.gimi.core.model.chaos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Steady state validation probe for chaos experiments.
 *
 * @param type       probe type (HTTP, CMD, PROMETHEUS)
 * @param endpoint   the probe endpoint or command
 * @param expected   expected result/status
 * @param interval   probe check interval (e.g., "5s")
 * @param timeout    probe timeout
 */
public record SteadyStateProbe(
        ProbeType type,
        String endpoint,
        String expected,
        String interval,
        String timeout
) {
    public SteadyStateProbe {
        type = type == null ? ProbeType.HTTP : type;
    }
}
