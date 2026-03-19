package dev.gimi.engine.executor;

import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.Step;
import dev.gimi.engine.variable.VariableInterpolator;

/**
 * Executes a pipeline step and returns the result.
 */
public interface StepExecutor {

    /**
     * Executes the given step and returns the result.
     *
     * @param step         the step to execute
     * @param interpolator the variable interpolator used to resolve placeholders
     * @return the result of executing the step
     */
    StepResult execute(Step step, VariableInterpolator interpolator);

    /**
     * Returns {@code true} if this executor can handle the given step type.
     *
     * @param step the step to check
     * @return {@code true} if this executor supports the step
     */
    boolean supports(Step step);
}
