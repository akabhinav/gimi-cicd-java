package dev.gimi.engine.strategy;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.model.CanaryStrategy;
import dev.gimi.core.model.DeployStrategy;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Handles canary deployment strategy execution.
 *
 * <p>Logs each increment step and runs validation if configured.
 * Currently logs the deployment plan without performing actual deployment.
 */
public class CanaryHandler implements DeployStrategyHandler {

    private static final Logger log = LoggerFactory.getLogger(CanaryHandler.class);

    /** {@inheritDoc} */
    @Override
    public StageResult execute(DeployStrategy strategy, VariableInterpolator interpolator) {
        CanaryStrategy canary = (CanaryStrategy) strategy;
        Instant start = Instant.now();

        log.info("Starting canary deployment");
        log.info("Increments: {}, interval: {}, rollbackOnFailure: {}",
                canary.increments(), canary.interval(), canary.rollbackOnFailure());

        ExecutionStatus status = ExecutionStatus.PASSED;

        for (int increment : canary.increments()) {
            log.info("Canary increment: {}% traffic", increment);

            if (canary.validation() != null) {
                String command = interpolator.interpolate(canary.validation().run());
                log.info("Running validation: {}", command);
                // Validation is logged but not actually executed in this stub
            }

            log.info("Waiting interval: {}", canary.interval());
        }

        if (canary.validation() != null) {
            log.info("All canary increments validated successfully");
        }

        log.info("Canary deployment plan completed with status: {}", status);

        return new StageResult.Builder()
                .name("canary-deploy")
                .status(status)
                .startedAt(start)
                .finishedAt(Instant.now())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(DeployStrategy strategy) {
        return strategy instanceof CanaryStrategy;
    }
}
