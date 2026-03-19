package dev.gimi.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTest {

    @Test
    void shouldCreateJobWithRequiredFields() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                Instant.now(), null, null, 0);

        assertThat(job.id()).isEqualTo("j1");
        assertThat(job.pipelineName()).isEqualTo("pipeline");
        assertThat(job.runId()).isEqualTo("run1");
        assertThat(job.stageName()).isEqualTo("build");
    }

    @Test
    void shouldDefaultStatusToQueued() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0);

        assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void shouldDefaultTimeoutToOneHour() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0);

        assertThat(job.timeoutSeconds()).isEqualTo(3600);
    }

    @Test
    void shouldDefaultNegativeTimeoutToOneHour() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, -5);

        assertThat(job.timeoutSeconds()).isEqualTo(3600);
    }

    @Test
    void shouldPreservePositiveTimeout() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 120);

        assertThat(job.timeoutSeconds()).isEqualTo(120);
    }

    @Test
    void shouldDefaultNullVariablesToEmptyMap() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0);

        assertThat(job.variables()).isEmpty();
        assertThat(job.secrets()).isEmpty();
    }

    @Test
    void shouldCreateDefensiveCopyOfVariables() {
        Map<String, String> vars = new HashMap<>();
        vars.put("key", "value");

        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, vars, null, 0, 0, 0,
                null, null, null, 3600);

        vars.put("extra", "val");

        assertThat(job.variables()).hasSize(1);
        assertThat(job.variables()).containsEntry("key", "value");
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> new Job(null, "pipeline", "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void shouldThrowWhenPipelineNameIsNull() {
        assertThatThrownBy(() -> new Job("j1", null, "run1", "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pipelineName");
    }

    @Test
    void shouldThrowWhenRunIdIsNull() {
        assertThatThrownBy(() -> new Job("j1", "pipeline", null, "build",
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void shouldThrowWhenStageNameIsNull() {
        assertThatThrownBy(() -> new Job("j1", "pipeline", "run1", null,
                null, null, null, null, null, 0, 0, 0,
                null, null, null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stageName");
    }

    @Test
    void shouldPreserveExplicitStatus() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                JobStatus.RUNNING, "worker1", null, null, null, 5, 3, 1,
                Instant.now(), Instant.now(), null, 600);

        assertThat(job.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.workerId()).isEqualTo("worker1");
        assertThat(job.priority()).isEqualTo(5);
        assertThat(job.maxRetries()).isEqualTo(3);
        assertThat(job.retryCount()).isEqualTo(1);
    }

    @Test
    void shouldProduceImmutableVariables() {
        Job job = new Job("j1", "pipeline", "run1", "build",
                null, null, null, Map.of("k", "v"), null, 0, 0, 0,
                null, null, null, 0);

        assertThatThrownBy(() -> job.variables().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldSupportEquality() {
        Instant now = Instant.now();
        Job j1 = new Job("j1", "pipe", "run1", "build",
                JobStatus.QUEUED, null, null, Map.of(), Map.of(), 0, 0, 0,
                now, null, null, 3600);
        Job j2 = new Job("j1", "pipe", "run1", "build",
                JobStatus.QUEUED, null, null, Map.of(), Map.of(), 0, 0, 0,
                now, null, null, 3600);

        assertThat(j1).isEqualTo(j2);
    }
}
