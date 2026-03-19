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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory implementation of {@link JobQueue} for testing and single-node deployments.
 *
 * <p>Uses a {@link ConcurrentLinkedDeque} for the priority queue and a
 * {@link ConcurrentHashMap} for job storage. Thread-safe but not distributed.
 */
public final class InMemoryJobQueue implements JobQueue {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryJobQueue.class);

    private final ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Instant> heartbeats = new ConcurrentHashMap<>();
    private final ReentrantLock dequeueLock = new ReentrantLock();

    @Override
    public void enqueue(Job job) {
        jobs.put(job.id(), job);
        // Insert in priority order (lower priority value = higher priority)
        dequeueLock.lock();
        try {
            queue.addLast(job.id());
        } finally {
            dequeueLock.unlock();
        }
        LOG.debug("Enqueued job: id={}, priority={}, pipeline={}", job.id(), job.priority(), job.pipelineName());
    }

    @Override
    public Optional<Job> dequeue(String workerId, Set<String> labels) {
        dequeueLock.lock();
        try {
            // Find the highest-priority job (lowest priority value) in the queue
            String bestId = null;
            int bestPriority = Integer.MAX_VALUE;

            for (String jobId : queue) {
                Job job = jobs.get(jobId);
                if (job != null && job.priority() < bestPriority) {
                    bestPriority = job.priority();
                    bestId = jobId;
                }
            }

            if (bestId == null) {
                return Optional.empty();
            }

            queue.remove(bestId);
            Job job = jobs.get(bestId);

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
        } finally {
            dequeueLock.unlock();
        }
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
}
