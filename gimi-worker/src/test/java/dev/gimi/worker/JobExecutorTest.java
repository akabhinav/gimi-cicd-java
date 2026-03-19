package dev.gimi.worker;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;
import dev.gimi.core.model.LogEntry;
import dev.gimi.engine.executor.StepExecutor;
import dev.gimi.engine.executor.StepExecutorRegistry;
import dev.gimi.engine.log.LogStreamer;
import dev.gimi.engine.variable.VariableInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private LogStreamer logStreamer;

    private StepExecutor mockStepExecutor;
    private JobExecutor jobExecutor;

    private static final String SIMPLE_PIPELINE_YAML = """
            version: "1"
            name: test-pipeline
            stages:
              - name: build
                steps:
                  - type: shell
                    name: compile
                    run: echo hello
            """;

    private static final String TWO_STEP_PIPELINE_YAML = """
            version: "1"
            name: test-pipeline
            stages:
              - name: build
                steps:
                  - type: shell
                    name: step1
                    run: echo step1
                  - type: shell
                    name: step2
                    run: echo step2
            """;

    @BeforeEach
    void setUp() {
        mockStepExecutor = mock(StepExecutor.class);
        lenient().when(mockStepExecutor.supports(any())).thenReturn(true);
        StepExecutorRegistry registry = new StepExecutorRegistry(mockStepExecutor);
        jobExecutor = new JobExecutor(registry, logStreamer);
    }

    private Job createJob(String id, String stageName, String pipelineYaml) {
        return new Job(id, "test-pipeline", "run-1", stageName,
                JobStatus.RUNNING, "worker-1", pipelineYaml,
                Map.of(), Map.of(), 0, 0, 0,
                Instant.now(), Instant.now(), null, 3600);
    }

    @Test
    void shouldExecuteJobSuccessfully() {
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("compile", ExecutionStatus.PASSED, 0, "ok", "", 100));

        Job job = createJob("j1", "build", SIMPLE_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.stageResult()).isNotNull();
        assertThat(result.stageResult().name()).isEqualTo("build");
        assertThat(result.stageResult().status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldReturnFailedWhenStepFails() {
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("compile", ExecutionStatus.FAILED, 1, "", "error", 100));

        Job job = createJob("j1", "build", SIMPLE_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.stageResult()).isNotNull();
        assertThat(result.stageResult().status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void shouldStopAtFirstFailedStep() {
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("step1", ExecutionStatus.FAILED, 1, "", "error", 100));

        Job job = createJob("j1", "build", TWO_STEP_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        // Should have only executed one step before stopping
        verify(mockStepExecutor, times(1)).execute(any(), any());
    }

    @Test
    void shouldExecuteAllStepsWhenAllPass() {
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("step1", ExecutionStatus.PASSED, 0, "ok", "", 50))
                .thenReturn(new StepResult("step2", ExecutionStatus.PASSED, 0, "ok", "", 50));

        Job job = createJob("j1", "build", TWO_STEP_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
        verify(mockStepExecutor, times(2)).execute(any(), any());
    }

    @Test
    void shouldPublishLogEntries() {
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("compile", ExecutionStatus.PASSED, 0, "ok", "", 100));

        Job job = createJob("j1", "build", SIMPLE_PIPELINE_YAML);

        jobExecutor.execute(job, "worker-1");

        verify(logStreamer, atLeastOnce()).publish(any(LogEntry.class));
    }

    @Test
    void shouldReturnFailedForInvalidPipelineYaml() {
        Job job = createJob("j1", "build", "invalid yaml content [[[");

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.stageResult()).isNull();
    }

    @Test
    void shouldReturnFailedForUnknownStage() {
        Job job = createJob("j1", "nonexistent-stage", SIMPLE_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.stageResult()).isNull();
    }

    @Test
    void shouldHandleStepExecutorException() {
        when(mockStepExecutor.execute(any(), any()))
                .thenThrow(new RuntimeException("executor error"));

        Job job = createJob("j1", "build", SIMPLE_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void shouldContinuePublishingEvenWhenLogStreamerFails() {
        doThrow(new RuntimeException("log error")).when(logStreamer).publish(any());
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("compile", ExecutionStatus.PASSED, 0, "ok", "", 100));

        Job job = createJob("j1", "build", SIMPLE_PIPELINE_YAML);

        // Should not throw despite log publishing failures
        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.status()).isEqualTo(ExecutionStatus.PASSED);
    }

    @Test
    void shouldTrackExecutionDuration() {
        when(mockStepExecutor.execute(any(), any()))
                .thenReturn(new StepResult("compile", ExecutionStatus.PASSED, 0, "ok", "", 100));

        Job job = createJob("j1", "build", SIMPLE_PIPELINE_YAML);

        JobExecutor.JobResult result = jobExecutor.execute(job, "worker-1");

        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }
}
