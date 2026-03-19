package dev.gimi.worker;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.Job;
import dev.gimi.core.model.LogEntry;
import dev.gimi.core.model.LogLevel;
import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.Stage;
import dev.gimi.core.model.Step;
import dev.gimi.engine.executor.StepExecutor;
import dev.gimi.engine.executor.StepExecutorRegistry;
import dev.gimi.engine.log.LogStreamer;
import dev.gimi.engine.parser.PipelineParser;
import dev.gimi.engine.variable.BuiltInVariableProvider;
import dev.gimi.engine.variable.ExecutionContext;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Handles the actual execution of a single job.
 *
 * <p>Parses the pipeline YAML from the job, creates an {@link ExecutionContext},
 * executes the target stage's steps sequentially using the {@link StepExecutorRegistry},
 * and streams log entries to the {@link LogStreamer} in real-time.
 */
@Component
public class JobExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);

    private final PipelineParser pipelineParser;
    private final StepExecutorRegistry executorRegistry;
    private final LogStreamer logStreamer;
    private final BuiltInVariableProvider builtInVariableProvider;

    /**
     * Result of executing a single job.
     *
     * @param status      the final execution status
     * @param stageResult the detailed stage result, or {@code null} if parsing failed
     * @param durationMs  the wall-clock duration of the execution in milliseconds
     */
    public record JobResult(
            ExecutionStatus status,
            StageResult stageResult,
            long durationMs
    ) {
    }

    public JobExecutor(StepExecutorRegistry executorRegistry,
                       LogStreamer logStreamer) {
        this.pipelineParser = new PipelineParser();
        this.executorRegistry = executorRegistry;
        this.logStreamer = logStreamer;
        this.builtInVariableProvider = new BuiltInVariableProvider();
    }

    /**
     * Executes the job by parsing its pipeline YAML, locating the target stage,
     * and running each step sequentially.
     *
     * @param job      the job to execute
     * @param workerId the ID of the worker executing this job
     * @return the result of the execution
     */
    public JobResult execute(Job job, String workerId) {
        Instant start = Instant.now();
        publishLog(job, workerId, null, LogLevel.INFO,
                "Starting execution of stage '%s' for pipeline '%s'".formatted(
                        job.stageName(), job.pipelineName()));

        try {
            Pipeline pipeline = pipelineParser.parse(job.pipelineYaml());

            Stage targetStage = pipeline.stages().stream()
                    .filter(s -> s.name().equals(job.stageName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stage '%s' not found in pipeline '%s'".formatted(
                                    job.stageName(), job.pipelineName())));

            Map<String, String> builtins = builtInVariableProvider.provide(
                    job.runId(), job.pipelineName());
            ExecutionContext context = new ExecutionContext(
                    job.variables(), job.secrets(), builtins, null, false);

            if (targetStage.environment() != null) {
                context = context.withEnvironment(pipeline, targetStage.environment());
            }

            VariableInterpolator interpolator = context.interpolator();

            StageResult.Builder stageBuilder = new StageResult.Builder()
                    .name(targetStage.name())
                    .startedAt(Instant.now());

            boolean stageFailed = false;

            for (Step step : targetStage.steps()) {
                publishLog(job, workerId, step.name(), LogLevel.INFO,
                        "Executing step: " + step.name());

                try {
                    StepExecutor executor = executorRegistry.executorFor(step);
                    StepResult stepResult = executor.execute(step, interpolator);
                    stageBuilder.addStepResult(stepResult);

                    if (stepResult.status() == ExecutionStatus.FAILED) {
                        publishLog(job, workerId, step.name(), LogLevel.ERROR,
                                "Step '%s' failed with exit code %d".formatted(
                                        step.name(), stepResult.exitCode()));
                        if (stepResult.stderr() != null && !stepResult.stderr().isBlank()) {
                            publishLog(job, workerId, step.name(), LogLevel.ERROR,
                                    stepResult.stderr());
                        }
                        stageFailed = true;
                        break;
                    }

                    publishLog(job, workerId, step.name(), LogLevel.INFO,
                            "Step '%s' completed successfully in %d ms".formatted(
                                    step.name(), stepResult.durationMs()));

                    if (stepResult.stdout() != null && !stepResult.stdout().isBlank()) {
                        publishLog(job, workerId, step.name(), LogLevel.DEBUG,
                                stepResult.stdout());
                    }

                } catch (Exception e) {
                    StepResult failedResult = new StepResult(
                            step.name(), ExecutionStatus.FAILED, 1,
                            "", e.getMessage(),
                            Duration.between(start, Instant.now()).toMillis());
                    stageBuilder.addStepResult(failedResult);

                    publishLog(job, workerId, step.name(), LogLevel.ERROR,
                            "Step '%s' threw exception: %s".formatted(
                                    step.name(), e.getMessage()));
                    stageFailed = true;
                    break;
                }
            }

            ExecutionStatus finalStatus = stageFailed
                    ? ExecutionStatus.FAILED : ExecutionStatus.PASSED;
            StageResult stageResult = stageBuilder
                    .status(finalStatus)
                    .finishedAt(Instant.now())
                    .build();

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            publishLog(job, workerId, null,
                    stageFailed ? LogLevel.ERROR : LogLevel.INFO,
                    "Stage '%s' finished with status %s in %d ms".formatted(
                            targetStage.name(), finalStatus, durationMs));

            return new JobResult(finalStatus, stageResult, durationMs);

        } catch (Exception e) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            LOG.error("Job execution failed for job {}: {}", job.id(), e.getMessage(), e);
            publishLog(job, workerId, null, LogLevel.ERROR,
                    "Job execution failed: " + e.getMessage());
            return new JobResult(ExecutionStatus.FAILED, null, durationMs);
        }
    }

    private void publishLog(Job job, String workerId, String stepName,
                            LogLevel level, String message) {
        try {
            LogEntry entry = new LogEntry(
                    job.runId(), job.stageName(), stepName,
                    level, message, Instant.now(), workerId);
            logStreamer.publish(entry);
        } catch (Exception e) {
            LOG.warn("Failed to publish log entry for job {}: {}", job.id(), e.getMessage());
        }
    }
}
