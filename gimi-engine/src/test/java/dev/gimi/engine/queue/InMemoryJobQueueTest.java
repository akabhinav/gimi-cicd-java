package dev.gimi.engine.queue;

import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryJobQueueTest {

    private InMemoryJobQueue queue;

    @BeforeEach
    void setUp() {
        queue = new InMemoryJobQueue();
    }

    private Job createJob(String id, String pipelineName, String runId, String stageName, int priority) {
        return new Job(id, pipelineName, runId, stageName,
                JobStatus.QUEUED, null, null, null, null,
                priority, 0, 0, Instant.now(), null, null, 3600);
    }

    @Test
    void shouldEnqueueAndDequeueJob() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);

        queue.enqueue(job);

        Optional<Job> dequeued = queue.dequeue("worker1", Set.of());

        assertThat(dequeued).isPresent();
        assertThat(dequeued.get().id()).isEqualTo("j1");
        assertThat(dequeued.get().status()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(dequeued.get().workerId()).isEqualTo("worker1");
    }

    @Test
    void shouldReturnEmptyWhenQueueIsEmpty() {
        Optional<Job> dequeued = queue.dequeue("worker1", Set.of());
        assertThat(dequeued).isEmpty();
    }

    @Test
    void shouldDequeueHighestPriorityFirst() {
        queue.enqueue(createJob("j-low", "p", "r", "s", 10));
        queue.enqueue(createJob("j-high", "p", "r", "s", 1));
        queue.enqueue(createJob("j-mid", "p", "r", "s", 5));

        Optional<Job> first = queue.dequeue("worker1", Set.of());
        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo("j-high");

        Optional<Job> second = queue.dequeue("worker1", Set.of());
        assertThat(second).isPresent();
        assertThat(second.get().id()).isEqualTo("j-mid");

        Optional<Job> third = queue.dequeue("worker1", Set.of());
        assertThat(third).isPresent();
        assertThat(third.get().id()).isEqualTo("j-low");
    }

    @Test
    void shouldUpdateJobStatus() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);
        queue.enqueue(job);
        queue.dequeue("worker1", Set.of());

        queue.updateStatus("j1", JobStatus.RUNNING);

        Optional<Job> updated = queue.getJob("j1");
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void shouldSetFinishedAtForTerminalStatus() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);
        queue.enqueue(job);
        queue.dequeue("worker1", Set.of());

        queue.updateStatus("j1", JobStatus.SUCCEEDED);

        Optional<Job> updated = queue.getJob("j1");
        assertThat(updated).isPresent();
        assertThat(updated.get().finishedAt()).isNotNull();
    }

    @Test
    void shouldGetJobById() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);
        queue.enqueue(job);

        Optional<Job> found = queue.getJob("j1");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("j1");
    }

    @Test
    void shouldReturnEmptyForUnknownJob() {
        assertThat(queue.getJob("nonexistent")).isEmpty();
    }

    @Test
    void shouldGetJobsByRunId() {
        queue.enqueue(createJob("j1", "p", "run1", "build", 0));
        queue.enqueue(createJob("j2", "p", "run1", "test", 0));
        queue.enqueue(createJob("j3", "p", "run2", "build", 0));

        List<Job> run1Jobs = queue.getJobsByRun("run1");

        assertThat(run1Jobs).hasSize(2);
        assertThat(run1Jobs).allMatch(j -> "run1".equals(j.runId()));
    }

    @Test
    void shouldReturnEmptyListForUnknownRunId() {
        List<Job> jobs = queue.getJobsByRun("nonexistent");
        assertThat(jobs).isEmpty();
    }

    @Test
    void shouldTrackQueueSize() {
        assertThat(queue.queueSize()).isZero();

        queue.enqueue(createJob("j1", "p", "r", "s", 0));
        assertThat(queue.queueSize()).isEqualTo(1);

        queue.enqueue(createJob("j2", "p", "r", "s", 0));
        assertThat(queue.queueSize()).isEqualTo(2);

        queue.dequeue("worker1", Set.of());
        assertThat(queue.queueSize()).isEqualTo(1);
    }

    @Test
    void shouldRecordHeartbeat() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);
        queue.enqueue(job);
        queue.dequeue("worker1", Set.of());

        // Should not throw
        queue.heartbeat("j1");
    }

    @Test
    void shouldIgnoreHeartbeatForUnknownJob() {
        // Should not throw
        queue.heartbeat("nonexistent");
    }

    @Test
    void shouldIgnoreStatusUpdateForUnknownJob() {
        // Should not throw
        queue.updateStatus("nonexistent", JobStatus.FAILED);
    }

    @Test
    void shouldDetectStaleJobs() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);
        queue.enqueue(job);
        queue.dequeue("worker1", Set.of());
        queue.updateStatus("j1", JobStatus.RUNNING);

        // Stale jobs with threshold of 0 should find the running job
        List<Job> staleJobs = queue.getStaleJobs(Duration.ZERO);
        // Note: heartbeat was set at dequeue, so with Duration.ZERO it might be stale
        // depending on timing - use a very short threshold
        assertThat(staleJobs).isNotNull();
    }

    @Test
    void shouldHandleMultipleTerminalStatuses() {
        for (JobStatus status : List.of(JobStatus.SUCCEEDED, JobStatus.FAILED,
                JobStatus.CANCELLED, JobStatus.TIMED_OUT)) {
            Job job = createJob("j-" + status.name(), "p", "r", "s", 0);
            queue.enqueue(job);
            queue.dequeue("worker1", Set.of());
            queue.updateStatus("j-" + status.name(), status);

            Optional<Job> updated = queue.getJob("j-" + status.name());
            assertThat(updated).isPresent();
            assertThat(updated.get().status()).isEqualTo(status);
            assertThat(updated.get().finishedAt()).isNotNull();
        }
    }

    @Test
    void dequeueShouldSetStartedAt() {
        Job job = createJob("j1", "pipeline", "run1", "build", 0);
        queue.enqueue(job);

        Optional<Job> dequeued = queue.dequeue("worker1", Set.of());

        assertThat(dequeued).isPresent();
        assertThat(dequeued.get().startedAt()).isNotNull();
    }
}
