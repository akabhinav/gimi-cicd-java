package dev.gimi.core.execution;

/**
 * Immutable result of a single step execution within a stage.
 *
 * @param name       the name of the step
 * @param status     the final execution status
 * @param exitCode   the process exit code, or {@code null} if not applicable
 * @param stdout     the captured standard output
 * @param stderr     the captured standard error output
 * @param durationMs the wall-clock duration of the step in milliseconds
 */
public record StepResult(
        String name,
        ExecutionStatus status,
        Integer exitCode,
        String stdout,
        String stderr,
        long durationMs
) {
}
