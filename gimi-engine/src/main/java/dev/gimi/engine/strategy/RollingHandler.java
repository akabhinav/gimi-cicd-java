package dev.gimi.engine.strategy;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.model.DeployStrategy;
import dev.gimi.core.model.RollingStrategy;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Handles rolling deployment strategy execution.
 *
 * <p>Logs the rolling update plan. Currently logs the deployment plan
 * without performing actual deployment.
 */
public class RollingHandler implements DeployStrategyHandler {

    private static final Logger log = LoggerFactory.getLogger(RollingHandler.class);

    /** {@inheritDoc} */
    @Override
    public StageResult execute(DeployStrategy strategy, VariableInterpolator interpolator) {
        RollingStrategy rolling = (RollingStrategy) strategy;
        Instant start = Instant.now();

        log.info("Starting rolling deployment");
        log.info("Max unavailable: {}", rolling.maxUnavailable());
        log.info("Plan: update instances incrementally with at most {} unavailable at a time",
                rolling.maxUnavailable());

        log.info("Rolling deployment plan completed successfully");

        return new StageResult.Builder()
                .name("rolling-deploy")
                .status(ExecutionStatus.PASSED)
                .startedAt(start)
                .finishedAt(Instant.now())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public boolean supports(DeployStrategy strategy) {
        return strategy instanceof RollingStrategy;
    }
}
