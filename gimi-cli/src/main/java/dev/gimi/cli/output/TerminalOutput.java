package dev.gimi.cli.output;

import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.execution.StepResult;
import org.fusesource.jansi.Ansi;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Utility class for colored terminal output using Jansi.
 *
 * <p>Provides static helper methods to print formatted, color-coded messages
 * to the console for pipeline execution feedback, including success/error messages,
 * stage headers, step results, and pipeline summaries.
 */
public final class TerminalOutput {

    private TerminalOutput() {
        // utility class
    }

    /**
     * Prints a green success message with a checkmark prefix.
     *
     * @param message the success message to display
     */
    public static void success(String message) {
        System.out.println(ansi().fgGreen().a("\u2713 ").a(message).reset());
    }

    /**
     * Prints a red error message with an X prefix.
     *
     * @param message the error message to display
     */
    public static void error(String message) {
        System.out.println(ansi().fgRed().a("\u2717 ").a(message).reset());
    }

    /**
     * Prints a yellow warning message.
     *
     * @param message the warning message to display
     */
    public static void warning(String message) {
        System.out.println(ansi().fgYellow().a(message).reset());
    }

    /**
     * Prints a blue informational message.
     *
     * @param message the informational message to display
     */
    public static void info(String message) {
        System.out.println(ansi().fgBlue().a(message).reset());
    }

    /**
     * Prints a stage header with a blue bullet and the stage name.
     *
     * @param stageName the name of the stage to display
     */
    public static void stageHeader(String stageName) {
        System.out.println(ansi().fgBlue().a("\u2022 ").bold().a(stageName).boldOff().reset());
    }

    /**
     * Prints a step that passed with a green checkmark, name, and duration.
     *
     * @param name       the name of the step
     * @param durationMs the duration of the step in milliseconds
     */
    public static void stepPassed(String name, long durationMs) {
        System.out.println(ansi().fgGreen().a("  \u2713 ").a(name).reset()
                .a(" (").a(formatDuration(durationMs)).a(")"));
    }

    /**
     * Prints a step that failed with a red X, name, duration, and failure reason.
     *
     * @param name       the name of the step
     * @param durationMs the duration of the step in milliseconds
     * @param reason     the reason the step failed
     */
    public static void stepFailed(String name, long durationMs, String reason) {
        System.out.println(ansi().fgRed().a("  \u2717 ").a(name).reset()
                .a(" (").a(formatDuration(durationMs)).a(")"));
        System.out.println(ansi().fgRed().a("    ").a(reason).reset());
    }

    /**
     * Prints a step that was skipped with a yellow dash and the step name.
     *
     * @param name the name of the skipped step
     */
    public static void stepSkipped(String name) {
        System.out.println(ansi().fgYellow().a("  - ").a(name).a(" (skipped)").reset());
    }

    /**
     * Prints a pipeline execution summary with pass/fail/skip counts and total duration.
     *
     * @param record the execution record containing stage and step results
     */
    public static void pipelineSummary(ExecutionRecord record) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        for (StageResult stage : record.stages()) {
            for (StepResult step : stage.steps()) {
                switch (step.status()) {
                    case PASSED -> passed++;
                    case FAILED -> failed++;
                    case SKIPPED -> skipped++;
                    default -> { /* ignore other statuses */ }
                }
            }
        }

        long totalMs = 0;
        if (record.startedAt() != null && record.finishedAt() != null) {
            totalMs = record.finishedAt().toEpochMilli() - record.startedAt().toEpochMilli();
        }

        Ansi statusColor = record.status() == ExecutionStatus.PASSED
                ? ansi().fgGreen()
                : ansi().fgRed();

        System.out.println();
        System.out.println(statusColor.bold()
                .a("Pipeline ")
                .a(record.status() == ExecutionStatus.PASSED ? "PASSED" : "FAILED")
                .boldOff().reset()
                .a(" | ")
                .a(passed).a(" passed, ")
                .a(failed).a(" failed, ")
                .a(skipped).a(" skipped")
                .a(" | ")
                .a(formatDuration(totalMs)));
    }

    /**
     * Formats a duration in milliseconds as a human-readable string.
     *
     * <p>Durations under 60 seconds are formatted as {@code "12.3s"}.
     * Durations of 60 seconds or more are formatted as {@code "2m 15s"}.
     *
     * @param ms the duration in milliseconds
     * @return the formatted duration string
     */
    public static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        if (totalSeconds < 60) {
            return String.format("%.1fs", ms / 1000.0);
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%dm %ds", minutes, seconds);
    }
}
