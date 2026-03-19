package dev.gimi.engine.orchestrator;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.Environment;
import dev.gimi.core.model.FailureAction;
import dev.gimi.core.model.FreezeWindow;
import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.RunCondition;
import dev.gimi.core.model.Stage;
import dev.gimi.core.model.StageType;
import dev.gimi.core.model.Step;
import dev.gimi.engine.dag.ExecutionPlanner;
import dev.gimi.engine.executor.ApprovalExecutor;
import dev.gimi.engine.executor.StepExecutor;
import dev.gimi.engine.executor.StepExecutorRegistry;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.variable.ExecutionContext;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Main orchestration engine that executes pipelines using virtual threads.
 *
 * <p>The orchestrator coordinates pipeline execution by:
 * <ol>
 *   <li>Planning execution batches using the {@link ExecutionPlanner}</li>
 *   <li>Executing batches sequentially, with stages within each batch running in parallel</li>
 *   <li>Handling failure actions (abort, skip, retry), approval gates, and freeze windows</li>
 *   <li>Persisting execution records to an optional {@link ExecutionStore}</li>
 * </ol>
 */
public final class PipelineOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final StepExecutorRegistry executorRegistry;
    private final ExecutionStore store;
    private final ApprovalExecutor approvalExecutor;

    /**
     * Creates an orchestrator with the given executor registry and an optional execution store.
     *
     * @param executorRegistry the registry used to find step executors
     * @param store            the execution store for persisting results, or {@code null} to skip persistence
     */
    public PipelineOrchestrator(StepExecutorRegistry executorRegistry, ExecutionStore store) {
        this.executorRegistry = executorRegistry;
        this.store = store;
        this.approvalExecutor = new ApprovalExecutor();
    }

    /**
     * Executes the given pipeline within the specified execution context.
     *
     * <p>Stages are grouped into batches by the {@link ExecutionPlanner}. Each batch is
     * executed sequentially, but stages within a batch run in parallel using virtual threads.
     * The method handles {@link RunCondition#ALWAYS} stages, freeze window checks, and
     * failure propagation according to each stage's {@link FailureAction}.
     *
     * @param pipeline the pipeline to execute
     * @param ctx      the execution context containing variables, secrets, and flags
     * @return an immutable {@link ExecutionRecord} describing the execution outcome
     */
    public ExecutionRecord execute(Pipeline pipeline, ExecutionContext ctx) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        ExecutionRecord.Builder record = new ExecutionRecord.Builder(runId, pipeline.name());
        record.startedAt(Instant.now());

        LOG.info("Starting pipeline '{}' with run ID '{}'", pipeline.name(), runId);

        ExecutionPlanner planner = new ExecutionPlanner();
        List<List<String>> batches = planner.plan(pipeline);
        boolean pipelineFailed = false;

        for (List<String> batch : batches) {
            List<String> stagesToRun;
            if (pipelineFailed) {
                // When pipeline has failed, only run ALWAYS and ON_FAILURE stages
                stagesToRun = batch.stream()
                        .filter(name -> {
                            Stage stage = findStage(pipeline, name);
                            return stage.runOn() == RunCondition.ALWAYS
                                    || stage.runOn() == RunCondition.ON_FAILURE;
                        })
                        .toList();
                if (stagesToRun.isEmpty()) {
                    // Mark skipped stages
                    for (String name : batch) {
                        record.addStageResult(new StageResult.Builder()
                                .name(name)
                                .status(ExecutionStatus.SKIPPED)
                                .startedAt(Instant.now())
                                .finishedAt(Instant.now())
                                .build());
                    }
                    continue;
                }
            } else {
                stagesToRun = batch;
            }

            List<StageResult> results = executeBatch(pipeline, stagesToRun, ctx);
            results.forEach(record::addStageResult);

            boolean shouldAbort = results.stream()
                    .anyMatch(r -> r.status() == ExecutionStatus.FAILED
                            && findStage(pipeline, r.name()).onFailure() == FailureAction.ABORT);

            if (shouldAbort) {
                pipelineFailed = true;
            }
        }

        record.finishedAt(Instant.now());
        record.status(pipelineFailed ? ExecutionStatus.FAILED : ExecutionStatus.PASSED);

        ExecutionRecord result = record.build();
        if (store != null) {
            try {
                store.save(result);
            } catch (Exception e) {
                LOG.warn("Failed to save execution record: {}", e.getMessage());
            }
        }

        LOG.info("Pipeline '{}' run '{}' finished with status: {}",
                pipeline.name(), runId, result.status());
        return result;
    }

    private List<StageResult> executeBatch(Pipeline pipeline, List<String> stageNames,
                                           ExecutionContext ctx) {
        if (stageNames.isEmpty()) {
            return List.of();
        }

        if (stageNames.size() == 1) {
            return List.of(executeStage(pipeline, stageNames.getFirst(), ctx));
        }

        List<StageResult> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<StageResult>> futures = stageNames.stream()
                    .map(name -> executor.submit(() -> executeStage(pipeline, name, ctx)))
                    .toList();

            for (Future<StageResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    LOG.error("Error executing stage in batch", e);
                }
            }
        }

        return results;
    }

    private StageResult executeStage(Pipeline pipeline, String stageName, ExecutionContext ctx) {
        Stage stage = findStage(pipeline, stageName);
        StageResult.Builder builder = new StageResult.Builder()
                .name(stageName)
                .startedAt(Instant.now());

        LOG.info("Executing stage '{}'", stageName);

        // Check freeze window
        if (stage.environment() != null) {
            Environment env = pipeline.environments().get(stage.environment());
            if (env != null && isInFreezeWindow(env)) {
                LOG.warn("Stage '{}' skipped: environment '{}' is in a freeze window",
                        stageName, stage.environment());
                return builder
                        .status(ExecutionStatus.SKIPPED)
                        .finishedAt(Instant.now())
                        .build();
            }
        }

        // Handle approval stages
        if (stage.stageType() == StageType.APPROVAL) {
            try {
                boolean approved = approvalExecutor.requestApproval(stageName, stage.approvers());
                return builder
                        .status(approved ? ExecutionStatus.PASSED : ExecutionStatus.CANCELLED)
                        .finishedAt(Instant.now())
                        .build();
            } catch (Exception e) {
                LOG.error("Approval failed for stage '{}'", stageName, e);
                return builder
                        .status(ExecutionStatus.FAILED)
                        .finishedAt(Instant.now())
                        .build();
            }
        }

        // Merge environment variables into context
        ExecutionContext stageCtx = stage.environment() != null
                ? ctx.withEnvironment(pipeline, stage.environment())
                : ctx;
        VariableInterpolator interpolator = stageCtx.interpolator();

        // Execute steps sequentially
        for (Step step : stage.steps()) {
            StepResult stepResult = executeStep(step, interpolator);
            builder.addStepResult(stepResult);

            if (stepResult.status() == ExecutionStatus.FAILED) {
                switch (stage.onFailure()) {
                    case ABORT -> {
                        return builder
                                .status(ExecutionStatus.FAILED)
                                .finishedAt(Instant.now())
                                .build();
                    }
                    case SKIP -> {
                        LOG.info("Step '{}' failed but on_failure=skip, continuing",
                                step.name());
                    }
                    case RETRY -> {
                        LOG.info("Retrying step '{}'...", step.name());
                        StepResult retryResult = executeStep(step, interpolator);
                        builder.addStepResult(retryResult);
                        if (retryResult.status() == ExecutionStatus.FAILED) {
                            return builder
                                    .status(ExecutionStatus.FAILED)
                                    .finishedAt(Instant.now())
                                    .build();
                        }
                    }
                }
            }
        }

        return builder
                .status(ExecutionStatus.PASSED)
                .finishedAt(Instant.now())
                .build();
    }

    private StepResult executeStep(Step step, VariableInterpolator interpolator) {
        try {
            StepExecutor executor = executorRegistry.executorFor(step);
            return executor.execute(step, interpolator);
        } catch (Exception e) {
            return new StepResult(
                    step.name(),
                    ExecutionStatus.FAILED,
                    -1,
                    "",
                    e.getMessage(),
                    0
            );
        }
    }

    private Stage findStage(Pipeline pipeline, String name) {
        return pipeline.stages().stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ExecutionException(
                        "Stage '" + name + "' not found in pipeline '" + pipeline.name() + "'",
                        "Check the stage name and ensure it is defined in the pipeline"
                ));
    }

    private boolean isInFreezeWindow(Environment env) {
        if (env.freezeWindows() == null || env.freezeWindows().isEmpty()) {
            return false;
        }

        String now = Instant.now().toString();
        for (FreezeWindow window : env.freezeWindows()) {
            if (window.start() != null && window.end() != null) {
                if (now.compareTo(window.start()) >= 0 && now.compareTo(window.end()) <= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
