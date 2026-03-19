package dev.gimi.engine.strategy;

import dev.gimi.core.execution.StageResult;
import dev.gimi.core.model.DeployStrategy;
import dev.gimi.engine.variable.VariableInterpolator;

/** Handles deployment strategy execution. */
public interface DeployStrategyHandler {

    /** Execute the deployment strategy. */
    StageResult execute(DeployStrategy strategy, VariableInterpolator interpolator);

    /** Returns true if this handler supports the given strategy type. */
    boolean supports(DeployStrategy strategy);
}
