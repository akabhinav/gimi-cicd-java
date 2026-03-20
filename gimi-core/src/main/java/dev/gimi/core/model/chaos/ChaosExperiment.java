package dev.gimi.core.model.chaos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Chaos Engineering experiment integrated into CI/CD pipelines.
 *
 * <p>Enables resilience testing as a pipeline step — inject faults during canary
 * deployments to validate system behavior under failure conditions.
 *
 * @param name              experiment name
 * @param faultType         the type of fault to inject
 * @param target            target service/resource
 * @param duration          fault injection duration (e.g., "30s", "5m")
 * @param intensity         fault intensity (0.0-1.0)
 * @param steadyStateProbe  validation to run during/after chaos
 * @param abortOnFailure    abort experiment if steady state fails
 * @param parameters        fault-specific parameters
 * @param labels            metadata labels
 */
public record ChaosExperiment(
        String name,
        @JsonProperty("fault_type") ChaosFlaultType faultType,
        String target,
        String duration,
        double intensity,
        @JsonProperty("steady_state_probe") SteadyStateProbe steadyStateProbe,
        @JsonProperty("abort_on_failure") boolean abortOnFailure,
        Map<String, String> parameters,
        Map<String, String> labels
) {
    public ChaosExperiment {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        if (intensity <= 0) intensity = 0.5;
    }
}
