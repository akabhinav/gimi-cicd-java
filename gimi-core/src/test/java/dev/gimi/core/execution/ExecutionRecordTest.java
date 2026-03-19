package dev.gimi.core.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionRecordTest {

    @Test
    void builderShouldCreateRecordWithRequiredFields() {
        ExecutionRecord record = new ExecutionRecord.Builder("run-1", "my-pipeline")
                .build();

        assertThat(record.runId()).isEqualTo("run-1");
        assertThat(record.pipelineName()).isEqualTo("my-pipeline");
        assertThat(record.stages()).isEmpty();
    }

    @Test
    void builderShouldSetAllFields() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(60);

        ExecutionRecord record = new ExecutionRecord.Builder("run-1", "pipeline")
                .status(ExecutionStatus.PASSED)
                .startedAt(start)
                .finishedAt(end)
                .trigger("manual")
                .environment("production")
                .build();

        assertThat(record.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(record.startedAt()).isEqualTo(start);
        assertThat(record.finishedAt()).isEqualTo(end);
        assertThat(record.trigger()).isEqualTo("manual");
        assertThat(record.environment()).isEqualTo("production");
    }

    @Test
    void builderShouldAccumulateStageResults() {
        StageResult stage1 = new StageResult.Builder()
                .name("build")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build();
        StageResult stage2 = new StageResult.Builder()
                .name("test")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build();

        ExecutionRecord record = new ExecutionRecord.Builder("run-1", "pipeline")
                .addStageResult(stage1)
                .addStageResult(stage2)
                .build();

        assertThat(record.stages()).hasSize(2);
        assertThat(record.stages().get(0).name()).isEqualTo("build");
        assertThat(record.stages().get(1).name()).isEqualTo("test");
    }

    @Test
    void builderShouldThrowOnNullRunId() {
        assertThatThrownBy(() -> new ExecutionRecord.Builder(null, "pipeline"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void builderShouldThrowOnNullPipelineName() {
        assertThatThrownBy(() -> new ExecutionRecord.Builder("run-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pipelineName");
    }

    @Test
    void builderShouldThrowOnNullStageResult() {
        ExecutionRecord.Builder builder = new ExecutionRecord.Builder("run-1", "pipeline");

        assertThatThrownBy(() -> builder.addStageResult(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void stagesListShouldBeImmutable() {
        ExecutionRecord record = new ExecutionRecord.Builder("run-1", "pipeline")
                .build();

        assertThatThrownBy(() -> record.stages().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldSupportEqualityForIdenticalRecords() {
        Instant now = Instant.now();
        ExecutionRecord r1 = new ExecutionRecord.Builder("run-1", "pipeline")
                .status(ExecutionStatus.PASSED)
                .startedAt(now)
                .build();
        ExecutionRecord r2 = new ExecutionRecord.Builder("run-1", "pipeline")
                .status(ExecutionStatus.PASSED)
                .startedAt(now)
                .build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void stageResultBuilderShouldWork() {
        StepResult step = new StepResult("compile", ExecutionStatus.PASSED, 0, "output", "", 500);

        StageResult stage = new StageResult.Builder()
                .name("build")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .addStepResult(step)
                .build();

        assertThat(stage.name()).isEqualTo("build");
        assertThat(stage.status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(stage.steps()).hasSize(1);
        assertThat(stage.steps().get(0).name()).isEqualTo("compile");
        assertThat(stage.steps().get(0).exitCode()).isEqualTo(0);
        assertThat(stage.steps().get(0).durationMs()).isEqualTo(500);
    }

    @Test
    void stageResultStepsShouldBeImmutable() {
        StageResult stage = new StageResult.Builder()
                .name("build")
                .status(ExecutionStatus.PASSED)
                .build();

        assertThatThrownBy(() -> stage.steps().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stageResultBuilderShouldThrowOnNullStepResult() {
        StageResult.Builder builder = new StageResult.Builder().name("build");

        assertThatThrownBy(() -> builder.addStepResult(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCreateRecordDirectly() {
        Instant now = Instant.now();
        ExecutionRecord record = new ExecutionRecord("run-1", "pipeline",
                ExecutionStatus.FAILED, now, now.plusSeconds(10),
                List.of(), "push", "staging");

        assertThat(record.runId()).isEqualTo("run-1");
        assertThat(record.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(record.trigger()).isEqualTo("push");
        assertThat(record.environment()).isEqualTo("staging");
    }

    @Test
    void builderMethodsShouldReturnBuilderForChaining() {
        ExecutionRecord.Builder builder = new ExecutionRecord.Builder("run-1", "pipeline");

        assertThat(builder.status(ExecutionStatus.RUNNING)).isSameAs(builder);
        assertThat(builder.startedAt(Instant.now())).isSameAs(builder);
        assertThat(builder.finishedAt(Instant.now())).isSameAs(builder);
        assertThat(builder.trigger("manual")).isSameAs(builder);
        assertThat(builder.environment("prod")).isSameAs(builder);
    }
}
