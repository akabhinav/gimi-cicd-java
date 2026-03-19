package dev.gimi.engine.strategy;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.model.BlueGreenStrategy;
import dev.gimi.core.model.DeployStrategy;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Handles blue-green deployment strategy execution.
 *
 * <p>Logs the blue-green swap plan. Currently logs the deployment plan
 * without performing actual deployment.
 */
public class BlueGreenHandler implements DeployStrategyHandler {

    private static final Logger log = LoggerFactory.getLogger(BlueGreenHandler.class);

    /** {@inheritDoc} */
    @Override
    public StageResult execute(DeployStrategy strategy, VariableInterpolator interpolator) {
        BlueGreenStrategy blueGreen = (BlueGreenStrategy) strategy;
        Instant start = Instant.now();

        log.info("Starting blue-green deployment");
        log.info("Swap on success: {}", blueGreen.swapOnSuccess());

        if (blueGreen.swapOnSuccess()) {
            log.info("Plan: deploy to inactive environment, verify, then swap traffic");
        } else {
            log.info("Plan: deploy to inactive environment, verify only (no swap)");
        }

        log.info("Blue-green deployment plan completed successfully");

        return new StageResult.Builder()
                .name("blue-green-deploy")
                .status(ExecutionStatus.PASSED)
                .startedAt(start)
                .finishedAt(Instant.now())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(DeployStrategy strategy) {
        return strategy instanceof BlueGreenStrategy;
    }
}
