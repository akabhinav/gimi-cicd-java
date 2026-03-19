package dev.gimi.engine.executor;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.DockerStep;
import dev.gimi.core.model.ShellStep;
import dev.gimi.core.model.Step;
import dev.gimi.engine.variable.VariableInterpolator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepExecutorRegistryTest {

    @Test
    void shouldFindExecutorForShellStep() {
        StepExecutorRegistry registry = StepExecutorRegistry.createDefault();

        ShellStep step = new ShellStep("test", "echo hi", null, null, null);
        StepExecutor executor = registry.executorFor(step);

        assertThat(executor).isInstanceOf(ShellExecutor.class);
    }

    @Test
    void shouldFindExecutorForDockerStep() {
        StepExecutorRegistry registry = StepExecutorRegistry.createDefault();

        DockerStep step = new DockerStep("test", null, "nginx", null, null);
        StepExecutor executor = registry.executorFor(step);

        assertThat(executor).isInstanceOf(DockerExecutor.class);
    }

    @Test
    void shouldThrowWhenNoExecutorFound() {
        // Registry with no executors
        StepExecutorRegistry registry = new StepExecutorRegistry();

        ShellStep step = new ShellStep("test", "echo hi", null, null, null);

        assertThatThrownBy(() -> registry.executorFor(step))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("No executor found");
    }

    @Test
    void shouldUseFirstMatchingExecutor() {
        StepExecutor customShell = new StepExecutor() {
            @Override
            public StepResult execute(Step step, VariableInterpolator interpolator) {
                return new StepResult("custom", ExecutionStatus.PASSED, 0, "custom", "", 0);
            }

            @Override
            public boolean supports(Step step) {
                return step instanceof ShellStep;
            }
        };

        StepExecutorRegistry registry = new StepExecutorRegistry(customShell, new ShellExecutor());

        ShellStep step = new ShellStep("test", "echo hi", null, null, null);
        StepExecutor found = registry.executorFor(step);

        // Should get the custom one (first registered)
        VariableInterpolator interp = new VariableInterpolator(Map.of(), Map.of(), Map.of());
        StepResult result = found.execute(step, interp);
        assertThat(result.stdout()).isEqualTo("custom");
    }

    @Test
    void createDefaultShouldRegisterShellAndDockerExecutors() {
        StepExecutorRegistry registry = StepExecutorRegistry.createDefault();

        // Should be able to find both
        assertThat(registry.executorFor(new ShellStep("s", "echo", null, null, null)))
                .isNotNull();
        assertThat(registry.executorFor(new DockerStep("d", null, "img", null, null)))
                .isNotNull();
    }

    @Test
    void shouldCreateRegistryWithVarargs() {
        StepExecutorRegistry registry = new StepExecutorRegistry(new ShellExecutor());

        ShellStep step = new ShellStep("test", "echo hi", null, null, null);
        assertThat(registry.executorFor(step)).isInstanceOf(ShellExecutor.class);

        DockerStep dockerStep = new DockerStep("test", null, "nginx", null, null);
        assertThatThrownBy(() -> registry.executorFor(dockerStep))
                .isInstanceOf(ExecutionException.class);
    }
}
