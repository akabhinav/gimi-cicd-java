package dev.gimi.engine.executor;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.ShellStep;
import dev.gimi.core.model.Step;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.gimi.engine.executor.OutputBuffer.MAX_OUTPUT_BYTES;

/**
 * Executes {@link ShellStep} instances using {@link ProcessBuilder}.
 *
 * <p>The executor interpolates variables in the command string, enforces a
 * configurable timeout, and captures both stdout and stderr output.
 */
public final class ShellExecutor implements StepExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ShellExecutor.class);

    private static final long DEFAULT_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();
    private static final Pattern TIMEOUT_PATTERN = Pattern.compile("(\\d+)\\s*([smh])");

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} when the step is a {@link ShellStep}.
     */
    @Override
    public boolean supports(Step step) {
        return step instanceof ShellStep;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs the shell command defined in the {@link ShellStep} via {@code sh -c}.
     * Environment variables and a working directory are applied when configured.
     * The process is forcibly destroyed if it exceeds the step timeout.
     *
     * @throws ExecutionException if the process cannot be started
     */
    @Override
    public StepResult execute(Step step, VariableInterpolator interpolator) {
        ShellStep shell = (ShellStep) step;
        String resolvedCommand = interpolator.interpolate(shell.run());

        // Log the command template (not the resolved command which may contain secrets)
        LOG.info("Executing step '{}': {}", shell.name(), shell.run());

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", resolvedCommand);
        pb.redirectErrorStream(true);

        if (shell.workingDir() != null) {
            pb.directory(new File(shell.workingDir()));
        }

        if (shell.env() != null && !shell.env().isEmpty()) {
            pb.environment().putAll(shell.env());
        }

        long timeoutMs = parseTimeoutMs(shell.timeout());
        Instant start = Instant.now();

        try {
            Process process = pb.start();
            OutputBuffer stdout = new OutputBuffer();
            OutputBuffer stderr = new OutputBuffer();

            // Read output in a bounded buffer to prevent OOM on large outputs
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info("[{}] {}", shell.name(), line);
                    stdout.append(line);
                    stdout.append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long durationMs = Duration.between(start, Instant.now()).toMillis();

            if (!finished) {
                process.destroyForcibly();
                LOG.warn("Step '{}' timed out after {} ms", shell.name(), timeoutMs);
                return new StepResult(
                        shell.name(),
                        ExecutionStatus.FAILED,
                        null,
                        stdout.toString(),
                        "Process timed out after " + timeoutMs + " ms",
                        durationMs
                );
            }

            int exitCode = process.exitValue();
            ExecutionStatus status = exitCode == 0 ? ExecutionStatus.PASSED : ExecutionStatus.FAILED;

            LOG.info("Step '{}' finished with exit code {}", shell.name(), exitCode);

            return new StepResult(
                    shell.name(),
                    status,
                    exitCode,
                    stdout.toString(),
                    stderr.toString(),
                    durationMs
            );

        } catch (IOException e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            throw new ExecutionException(
                    "Failed to start process for step '" + shell.name() + "'",
                    "Ensure 'sh' is available on the system PATH",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            return new StepResult(
                    shell.name(),
                    ExecutionStatus.CANCELLED,
                    null,
                    "",
                    "Execution interrupted",
                    durationMs
            );
        }
    }

    /**
     * Parses a human-readable timeout string into milliseconds.
     *
     * <p>Supported formats: {@code "30s"} (seconds), {@code "5m"} (minutes),
     * {@code "1h"} (hours). Returns the default timeout of 10 minutes if
     * the input is {@code null} or cannot be parsed.
     *
     * @param timeout the timeout string to parse
     * @return the timeout in milliseconds
     */
    private long parseTimeoutMs(String timeout) {
        if (timeout == null || timeout.isBlank()) {
            return DEFAULT_TIMEOUT_MS;
        }

        Matcher matcher = TIMEOUT_PATTERN.matcher(timeout.trim().toLowerCase());
        if (!matcher.matches()) {
            LOG.warn("Invalid timeout format '{}', using default (10m)", timeout);
            return DEFAULT_TIMEOUT_MS;
        }

        long value = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "s" -> TimeUnit.SECONDS.toMillis(value);
            case "m" -> TimeUnit.MINUTES.toMillis(value);
            case "h" -> TimeUnit.HOURS.toMillis(value);
            default -> DEFAULT_TIMEOUT_MS;
        };
    }
}
