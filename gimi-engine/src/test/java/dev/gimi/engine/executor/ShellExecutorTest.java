package dev.gimi.engine.executor;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.DockerStep;
import dev.gimi.core.model.ShellStep;
import dev.gimi.engine.variable.VariableInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShellExecutorTest {

    private ShellExecutor executor;
    private VariableInterpolator interpolator;

    @BeforeEach
    void setUp() {
        executor = new ShellExecutor();
        interpolator = new VariableInterpolator(Map.of(), Map.of(), Map.of());
    }

    @Test
    void shouldSupportShellStep() {
        ShellStep step = new ShellStep("test", "echo hi", null, null, null);
        assertThat(executor.supports(step)).isTrue();
    }

    @Test
    void shouldNotSupportDockerStep() {
        DockerStep step = new DockerStep("test", null, "nginx", null, null);
        assertThat(executor.supports(step)).isFalse();
    }

    @Test
    void shouldExecuteEchoCommand() {
        ShellStep step = new ShellStep("echo-test", "echo hello", null, null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.name()).isEqualTo("echo-test");
        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("hello");
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldCaptureStdout() {
        ShellStep step = new ShellStep("output-test", "echo 'line1' && echo 'line2'",
                null, null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stdout()).contains("line1");
        assertThat(result.stdout()).contains("line2");
    }

    @Test
    void shouldReturnFailedForNonZeroExitCode() {
        ShellStep step = new ShellStep("fail-test", "exit 1", null, null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    void shouldReturnSpecificExitCode() {
        ShellStep step = new ShellStep("exit-code-test", "exit 42", null, null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.exitCode()).isEqualTo(42);
    }

    @Test
    void shouldInterpolateVariablesInCommand() {
        VariableInterpolator varInterpolator = new VariableInterpolator(
                Map.of("msg", "interpolated"), Map.of(), Map.of());

        ShellStep step = new ShellStep("interpolate-test", "echo ${msg}", null, null, null);

        StepResult result = executor.execute(step, varInterpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stdout()).contains("interpolated");
    }

    @Test
    void shouldHandleMultilineCommand() {
        ShellStep step = new ShellStep("multiline", "echo first && echo second",
                null, null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stdout()).contains("first");
        assertThat(result.stdout()).contains("second");
    }

    @Test
    void shouldRespectWorkingDirectory() {
        ShellStep step = new ShellStep("dir-test", "pwd", null, "/tmp", null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stdout().trim()).contains("/tmp");
    }

    @Test
    void shouldHandleShortTimeout() {
        ShellStep step = new ShellStep("timeout-test", "sleep 30", "1s", null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.stderr()).contains("timed out");
    }

    @Test
    void shouldTrackDuration() {
        ShellStep step = new ShellStep("duration-test", "sleep 0.1", null, null, null);

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.durationMs()).isGreaterThan(0);
    }

    @Test
    void shouldHandleEnvironmentVariables() {
        ShellStep step = new ShellStep("env-test", "echo $MY_VAR",
                null, null, Map.of("MY_VAR", "hello_env"));

        StepResult result = executor.execute(step, interpolator);

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stdout()).contains("hello_env");
    }
}
