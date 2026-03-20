package dev.gimi.engine.chaos;

import dev.gimi.core.model.chaos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chaos Engineering service for resilience testing within CI/CD pipelines.
 *
 * <p>Injects controlled faults during deployments to validate system behavior
 * under failure conditions — integrated with continuous verification for
 * automated steady-state validation.
 */
public class ChaosEngineService {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosEngineService.class);

    private final Map<String, ChaosResult> results = new ConcurrentHashMap<>();

    /**
     * Executes a chaos experiment.
     *
     * @param experiment the experiment configuration
     * @param runId      pipeline run ID
     * @return the experiment result
     */
    public ChaosResult executeExperiment(ChaosExperiment experiment, String runId) {
        LOG.info("Starting chaos experiment: name={}, fault={}, target={}, duration={}",
                experiment.name(), experiment.faultType(), experiment.target(), experiment.duration());

        Instant start = Instant.now();
        boolean steadyStateMet = true;
        double resilienceScore = 100.0;
        String errorMessage = null;

        try {
            // Inject fault
            injectFault(experiment);

            // Run steady state probe if configured
            if (experiment.steadyStateProbe() != null) {
                steadyStateMet = runSteadyStateProbe(experiment.steadyStateProbe());
                if (!steadyStateMet) {
                    resilienceScore = 0.0;
                    LOG.warn("Steady state probe FAILED for experiment: {}", experiment.name());
                }
            }

            // Wait for duration
            LOG.info("Chaos experiment '{}' running for {}", experiment.name(), experiment.duration());

            // Remove fault
            removeFault(experiment);

            // Final steady state check
            if (experiment.steadyStateProbe() != null && steadyStateMet) {
                steadyStateMet = runSteadyStateProbe(experiment.steadyStateProbe());
                resilienceScore = steadyStateMet ? 100.0 : 50.0;
            }

        } catch (Exception e) {
            errorMessage = e.getMessage();
            resilienceScore = 0.0;
            LOG.error("Chaos experiment '{}' encountered error: {}", experiment.name(), e.getMessage(), e);
            removeFault(experiment);
        }

        ChaosVerdict verdict = resilienceScore >= 80 ? ChaosVerdict.PASS
                : resilienceScore >= 50 ? ChaosVerdict.FAIL
                : ChaosVerdict.ERROR;

        ChaosResult result = new ChaosResult(
                experiment.name(), experiment.faultType(), verdict,
                steadyStateMet, resilienceScore, start, Instant.now(), errorMessage
        );

        results.put(runId + ":" + experiment.name(), result);

        LOG.info("Chaos experiment complete: name={}, verdict={}, resilience={:.1f}%",
                experiment.name(), verdict, resilienceScore);

        return result;
    }

    public Optional<ChaosResult> getResult(String runId, String experimentName) {
        return Optional.ofNullable(results.get(runId + ":" + experimentName));
    }

    private void injectFault(ChaosExperiment experiment) {
        LOG.info("Injecting fault: type={}, target={}, intensity={}",
                experiment.faultType(), experiment.target(), experiment.intensity());
        // In production, delegates to Litmus, Chaos Mesh, or native fault injection
    }

    private void removeFault(ChaosExperiment experiment) {
        LOG.info("Removing fault: type={}, target={}", experiment.faultType(), experiment.target());
    }

    private boolean runSteadyStateProbe(SteadyStateProbe probe) {
        LOG.info("Running steady state probe: type={}, endpoint={}", probe.type(), probe.endpoint());
        // In production, executes HTTP/CMD/Prometheus probe
        return true;
    }
}
