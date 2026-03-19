package dev.gimi.engine.executor;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.DockerStep;
import dev.gimi.core.model.Step;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor for {@link DockerStep} instances.
 *
 * <p>This implementation is currently a stub. All Docker steps are logged and
 * returned with a {@link ExecutionStatus#SKIPPED} status until full Docker
 * integration is implemented.
 */
public final class DockerExecutor implements StepExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DockerExecutor.class);

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} when the step is a {@link DockerStep}.
     */
    @Override
    public boolean supports(Step step) {
        return step instanceof DockerStep;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Logs a message indicating that Docker execution is not yet implemented
     * and returns a {@link ExecutionStatus#SKIPPED} result.
     */
    @Override
    public StepResult execute(Step step, VariableInterpolator interpolator) {
        DockerStep docker = (DockerStep) step;
        LOG.warn("Docker execution is not yet implemented; skipping step '{}'", docker.name());

        return new StepResult(
                docker.name(),
                ExecutionStatus.SKIPPED,
                null,
                "",
                "Docker execution is not yet implemented",
                0L
        );
    }
}
