package dev.gimi.engine.queue;

import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of {@link JobQueue} for testing and single-node deployments.
 *
 * <p>Uses a {@link PriorityBlockingQueue} for O(log n) priority-ordered dequeue
 * and a {@link ConcurrentHashMap} for O(1) job lookups. Thread-safe but not distributed.
 *
 * <p>Bounded at {@value #MAX_QUEUE_CAPACITY} jobs to prevent OOM under high load.
 * For production 10K+ workloads, use {@link RedisJobQueue} instead.
 */
public final class InMemoryJobQueue implements JobQueue {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryJobQueue.class);

    /** Maximum queued jobs to prevent unbounded memory growth. */
    static final int MAX_QUEUE_CAPACITY = 50_000;

    private final PriorityBlockingQueue<JobEntry> queue;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Instant> heartbeats = new ConcurrentHashMap<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public InMemoryJobQueue() {
        this.queue = new PriorityBlockingQueue<>(1024,
                Comparator.<JobEntry>comparingInt(e -> e.priority)
                        .thenComparingLong(e -> e.sequence));
    }

    @Override
    public void enqueue(Job job) {
        if (jobs.size() >= MAX_QUEUE_CAPACITY) {
            LOG.error("Job queue capacity exhausted ({} jobs). Rejecting job: id={}, pipeline={}",
                    MAX_QUEUE_CAPACITY, job.id(), job.pipelineName());
            throw new IllegalStateException("Job queue at capacity (" + MAX_QUEUE_CAPACITY
                    + "). Use RedisJobQueue for production workloads.");
        }

        jobs.put(job.id(), job);
        queue.offer(new JobEntry(job.id(), job.priority(), sequenceCounter.getAndIncrement()));
        LOG.debug("Enqueued job: id={}, priority={}, pipeline={}", job.id(), job.priority(), job.pipelineName());
    }

    @Override
    public Optional<Job> dequeue(String workerId, Set<String> labels) {
        // O(log n) poll from priority queue — no locking needed
        JobEntry entry = queue.poll();
        if (entry == null) {
            return Optional.empty();
        }

        Job job = jobs.get(entry.jobId);
        if (job == null) {
            // Job was removed (cancelled) between enqueue and dequeue
            LOG.debug("Skipped removed job: id={}", entry.jobId);
            return dequeue(workerId, labels); // try next
        }

        Job assigned = new Job(
                job.id(), job.pipelineName(), job.runId(), job.stageName(),
                JobStatus.ASSIGNED, workerId, job.pipelineYaml(),
                job.variables(), job.secrets(), job.priority(),
                job.maxRetries(), job.retryCount(), job.createdAt(),
                Instant.now(), null, job.timeoutSeconds()
        );

        jobs.put(assigned.id(), assigned);
        heartbeats.put(assigned.id(), Instant.now());

        LOG.debug("Dequeued job: id={}, assignedTo={}", assigned.id(), workerId);
        return Optional.of(assigned);
    }

    @Override
    public void updateStatus(String jobId, JobStatus status) {
        Job job = jobs.get(jobId);
        if (job == null) {
            LOG.warn("Cannot update status for unknown job: {}", jobId);
            return;
        }

        Instant finishedAt = isTerminal(status) ? Instant.now() : job.finishedAt();
        Job updated = new Job(
                job.id(), job.pipelineName(), job.runId(), job.stageName(),
                status, job.workerId(), job.pipelineYaml(),
                job.variables(), job.secrets(), job.priority(),
                job.maxRetries(), job.retryCount(), job.createdAt(),
                job.startedAt(), finishedAt, job.timeoutSeconds()
        );

        jobs.put(jobId, updated);

        if (isTerminal(status)) {
            heartbeats.remove(jobId);
        }

        LOG.debug("Updated job {} to status {}", jobId, status);
    }

    @Override
    public void heartbeat(String jobId) {
        if (jobs.containsKey(jobId)) {
            heartbeats.put(jobId, Instant.now());
        }
    }

    @Override
    public Optional<Job> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public List<Job> getJobsByRun(String runId) {
        return jobs.values().stream()
                .filter(j -> runId.equals(j.runId()))
                .toList();
    }

    @Override
    public List<Job> getStaleJobs(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        return jobs.values().stream()
                .filter(j -> j.status() == JobStatus.RUNNING || j.status() == JobStatus.ASSIGNED)
                .filter(j -> {
                    Instant lastBeat = heartbeats.get(j.id());
                    return lastBeat == null || lastBeat.isBefore(cutoff);
                })
                .toList();
    }

    @Override
    public long queueSize() {
        return queue.size();
    }

    private static boolean isTerminal(JobStatus status) {
        return status == JobStatus.SUCCEEDED
                || status == JobStatus.FAILED
                || status == JobStatus.CANCELLED
                || status == JobStatus.TIMED_OUT;
    }

    /**
     * Internal priority queue entry. Uses priority + insertion order for FIFO
     * within the same priority level.
     */
    private record JobEntry(String jobId, int priority, long sequence) {}
}
