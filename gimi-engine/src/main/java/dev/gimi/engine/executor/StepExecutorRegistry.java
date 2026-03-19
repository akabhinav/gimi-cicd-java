package dev.gimi.engine.executor;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.model.Step;

import java.util.List;

/**
 * Registry that maps pipeline step types to their corresponding executors.
 *
 * <p>The registry is queried at execution time to find the appropriate
 * {@link StepExecutor} for each {@link Step} encountered in a pipeline.
 */
public final class StepExecutorRegistry {

    private final List<StepExecutor> executors;

    /**
     * Creates a registry containing the given executors.
     *
     * @param executors the executors to register
     */
    public StepExecutorRegistry(StepExecutor... executors) {
        this.executors = List.of(executors);
    }

    /**
     * Creates a default registry pre-configured with {@link ShellExecutor}
     * and {@link DockerExecutor}.
     *
     * @return a new registry with the default set of executors
     */
    public static StepExecutorRegistry createDefault() {
        return new StepExecutorRegistry(new ShellExecutor(), new DockerExecutor());
    }

    /**
     * Finds the first registered executor that supports the given step.
     *
     * @param step the step to find an executor for
     * @return the matching executor
     * @throws ExecutionException if no registered executor supports the step
     */
    public StepExecutor executorFor(Step step) {
        return executors.stream()
                .filter(e -> e.supports(step))
                .findFirst()
                .orElseThrow(() -> new ExecutionException(
                        "No executor found for step type: " + step.getClass().getSimpleName(),
                        "Register a StepExecutor that supports " + step.getClass().getSimpleName()
                ));
    }
}
