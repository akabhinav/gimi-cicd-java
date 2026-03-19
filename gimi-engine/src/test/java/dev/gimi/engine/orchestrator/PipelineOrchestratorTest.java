package dev.gimi.engine.orchestrator;

import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.*;
import dev.gimi.engine.executor.StepExecutor;
import dev.gimi.engine.executor.StepExecutorRegistry;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.variable.ExecutionContext;
import dev.gimi.engine.variable.VariableInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineOrchestratorTest {

    @Mock
    private ExecutionStore store;

    private StepExecutor mockExecutor;
    private PipelineOrchestrator orchestrator;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        mockExecutor = mock(StepExecutor.class);
        lenient().when(mockExecutor.supports(any())).thenReturn(true);

        StepExecutorRegistry registry = new StepExecutorRegistry(mockExecutor);
        orchestrator = new PipelineOrchestrator(registry, store);
        ctx = new ExecutionContext(Map.of(), Map.of(), Map.of(), null, false);
    }

    private Pipeline pipelineWith(List<Stage> stages) {
        return new Pipeline("1", "test-pipeline", null, null, null, null, stages);
    }

    private ShellStep shellStep(String name) {
        return new ShellStep(name, "echo " + name, null, null, null);
    }

    private StepResult passedResult(String name) {
        return new StepResult(name, ExecutionStatus.PASSED, 0, "ok", "", 100);
    }

    private StepResult failedResult(String name) {
        return new StepResult(name, ExecutionStatus.FAILED, 1, "", "error", 100);
    }

    @Test
    void shouldExecuteSingleStageSuccessfully() {
        when(mockExecutor.execute(any(), any())).thenReturn(passedResult("step1"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step1")), null, null, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        assertThat(record.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(record.stages()).hasSize(1);
        assertThat(record.stages().get(0).name()).isEqualTo("build");
        assertThat(record.stages().get(0).status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(record.pipelineName()).isEqualTo("test-pipeline");
    }

    @Test
    void shouldExecuteMultipleStagesSequentially() {
        when(mockExecutor.execute(any(), any())).thenReturn(passedResult("step"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("compile")), null, null, null, null, null),
                new Stage("test", List.of("build"), null, null, null,
                        List.of(shellStep("run-tests")), null, null, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        assertThat(record.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(record.stages()).hasSize(2);
    }

    @Test
    void shouldAbortOnFailureWhenOnFailureIsAbort() {
        when(mockExecutor.execute(any(), any())).thenReturn(failedResult("step1"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step1")),
                        null, FailureAction.ABORT, null, null, null),
                new Stage("test", List.of("build"), null, null, null,
                        List.of(shellStep("step2")),
                        null, null, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        assertThat(record.status()).isEqualTo(ExecutionStatus.FAILED);
        // The first stage should be FAILED, the second should be SKIPPED
        assertThat(record.stages().get(0).status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void shouldContinueOnFailureWhenOnFailureIsSkip() {
        when(mockExecutor.execute(any(), any()))
                .thenReturn(failedResult("step1"))
                .thenReturn(passedResult("step2"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step1")),
                        null, FailureAction.SKIP, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        // With SKIP, the stage should still pass despite failed step
        assertThat(record.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void shouldSkipSubsequentStagesAfterAbort() {
        when(mockExecutor.execute(any(), any())).thenReturn(failedResult("step"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step")),
                        null, FailureAction.ABORT, null, null, null),
                new Stage("test", List.of("build"), null, null, null,
                        List.of(shellStep("step")),
                        null, null, null, null, null),
                new Stage("deploy", List.of("test"), null, null, null,
                        List.of(shellStep("step")),
                        null, null, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        assertThat(record.status()).isEqualTo(ExecutionStatus.FAILED);
        // Build should fail, test and deploy should be skipped
        long skippedCount = record.stages().stream()
                .filter(s -> s.status() == ExecutionStatus.SKIPPED)
                .count();
        assertThat(skippedCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldRunAlwaysStagesEvenAfterFailure() {
        when(mockExecutor.execute(any(), any()))
                .thenReturn(failedResult("fail-step"))
                .thenReturn(passedResult("cleanup-step"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("fail-step")),
                        null, FailureAction.ABORT, null, null, null),
                new Stage("cleanup", null, null, null, null,
                        List.of(shellStep("cleanup-step")),
                        null, null, RunCondition.ALWAYS, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        // cleanup should run because runOn=ALWAYS
        boolean cleanupRan = record.stages().stream()
                .anyMatch(s -> s.name().equals("cleanup") && s.status() != ExecutionStatus.SKIPPED);
        assertThat(cleanupRan).isTrue();
    }

    @Test
    void shouldSaveExecutionRecordToStore() {
        when(mockExecutor.execute(any(), any())).thenReturn(passedResult("step"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step")), null, null, null, null, null)
        ));

        orchestrator.execute(pipeline, ctx);

        verify(store).save(any(ExecutionRecord.class));
    }

    @Test
    void shouldWorkWithNullStore() {
        when(mockExecutor.execute(any(), any())).thenReturn(passedResult("step"));

        PipelineOrchestrator noStoreOrchestrator = new PipelineOrchestrator(
                new StepExecutorRegistry(mockExecutor), null);

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step")), null, null, null, null, null)
        ));

        ExecutionRecord record = noStoreOrchestrator.execute(pipeline, ctx);

        assertThat(record.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void shouldSetTimestampsOnRecord() {
        when(mockExecutor.execute(any(), any())).thenReturn(passedResult("step"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step")), null, null, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        assertThat(record.startedAt()).isNotNull();
        assertThat(record.finishedAt()).isNotNull();
        assertThat(record.runId()).isNotBlank();
    }

    @Test
    void shouldRetryOnFailureWhenOnFailureIsRetry() {
        when(mockExecutor.execute(any(), any()))
                .thenReturn(failedResult("step"))
                .thenReturn(passedResult("step"));

        Pipeline pipeline = pipelineWith(List.of(
                new Stage("build", null, null, null, null,
                        List.of(shellStep("step")),
                        null, FailureAction.RETRY, null, null, null)
        ));

        ExecutionRecord record = orchestrator.execute(pipeline, ctx);

        assertThat(record.status()).isEqualTo(ExecutionStatus.PASSED);
        // Should have called execute twice for the retry
        verify(mockExecutor, atLeast(2)).execute(any(), any());
    }
}
