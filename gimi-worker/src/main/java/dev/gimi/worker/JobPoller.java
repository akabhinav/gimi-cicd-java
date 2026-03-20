package dev.gimi.worker;

import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;
import dev.gimi.engine.queue.JobQueue;
import dev.gimi.worker.config.WorkerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core worker component that polls the job queue for available work and
 * submits jobs for parallel execution on virtual threads.
 *
 * <p>Optimized for 10,000+ concurrent pipeline deployments with:
 * <ul>
 *   <li>Adaptive polling: backs off exponentially when queue is empty, ramps up under load</li>
 *   <li>Circuit breaker: stops polling after consecutive failures to prevent cascade</li>
 *   <li>Batch dequeue: attempts to fill all available slots in a single poll cycle</li>
 *   <li>Backpressure: respects semaphore and skips polling when all slots are busy</li>
 * </ul>
 *
 * <p>Uses a {@link Semaphore} to limit concurrency to the configured
 * {@code maxConcurrentJobs}. Each polled job is submitted to a virtual
 * thread executor and wrapped with timeout handling and retry logic.
 */
@Component
public class JobPoller {

    private static final Logger LOG = LoggerFactory.getLogger(JobPoller.class);

    /** Maximum backoff when queue is empty (16 seconds). */
    private static final long MAX_BACKOFF_MS = 16_000;

    /** Consecutive failures before circuit breaker opens. */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    /** Time to wait before retrying after circuit breaker opens. */
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30_000;

    private final JobQueue jobQueue;
    private final JobExecutor jobExecutor;
    private final WorkerConfig config;
    private final Semaphore concurrencyLimit;
    private final ExecutorService virtualThreadExecutor;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final AtomicBoolean draining = new AtomicBoolean(false);

    // Adaptive polling state
    private final AtomicLong currentBackoffMs = new AtomicLong(0);
    private final AtomicLong lastPollTime = new AtomicLong(0);

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenedAt = new AtomicLong(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

    public JobPoller(JobQueue jobQueue, JobExecutor jobExecutor, WorkerConfig config) {
        this.jobQueue = jobQueue;
        this.jobExecutor = jobExecutor;
        this.config = config;
        this.concurrencyLimit = new Semaphore(config.getMaxConcurrentJobs());
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Periodically polls the job queue for available work. Respects the configured
     * poll interval, concurrency limits, adaptive backoff, and circuit breaker.
     */
    @Scheduled(fixedDelayString = "${gimi.worker.poll-interval-ms:500}")
    public void poll() {
        if (draining.get()) {
            LOG.debug("Worker is draining, skipping poll");
            return;
        }

        // Circuit breaker check
        if (circuitOpen.get()) {
            long elapsed = System.currentTimeMillis() - circuitOpenedAt.get();
            if (elapsed < CIRCUIT_BREAKER_COOLDOWN_MS) {
                LOG.debug("Circuit breaker open, cooldown remaining: {}ms",
                        CIRCUIT_BREAKER_COOLDOWN_MS - elapsed);
                return;
            }
            // Half-open: allow one attempt
            LOG.info("Circuit breaker half-open, attempting recovery poll");
            circuitOpen.set(false);
        }

        // Adaptive backoff: skip poll if within backoff window
        long now = System.currentTimeMillis();
        long backoff = currentBackoffMs.get();
        if (backoff > 0 && (now - lastPollTime.get()) < backoff) {
            return;
        }
        lastPollTime.set(now);

        // Batch dequeue: try to fill all available slots
        int availableSlots = concurrencyLimit.availablePermits();
        if (availableSlots == 0) {
            LOG.debug("All job slots occupied ({}/{}), skipping poll",
                    activeJobCount.get(), config.getMaxConcurrentJobs());
            return;
        }

        int dequeued = 0;
        try {
            for (int i = 0; i < availableSlots; i++) {
                if (!concurrencyLimit.tryAcquire()) {
                    break;
                }

                try {
                    Optional<Job> maybeJob = jobQueue.dequeue(
                            config.getWorkerId(), config.getLabels());

                    if (maybeJob.isEmpty()) {
                        concurrencyLimit.release();
                        break; // Queue is empty, stop trying
                    }

                    Job job = maybeJob.get();
                    LOG.info("Dequeued job: id={}, pipeline={}, stage={}",
                            job.id(), job.pipelineName(), job.stageName());

                    activeJobCount.incrementAndGet();
                    dequeued++;
                    submitJob(job);
                } catch (Exception e) {
                    concurrencyLimit.release();
                    LOG.error("Error during job dequeue: {}", e.getMessage(), e);
                    onPollFailure();
                    return;
                }
            }

            // Adaptive backoff adjustment
            if (dequeued > 0) {
                // Got work — reset backoff to poll aggressively
                currentBackoffMs.set(0);
                consecutiveFailures.set(0);
            } else {
                // No work — increase backoff exponentially
                long newBackoff = Math.min(
                        Math.max(config.getPollIntervalMs(), currentBackoffMs.get() * 2),
                        MAX_BACKOFF_MS);
                currentBackoffMs.set(newBackoff);
                LOG.debug("Queue empty, backoff increased to {}ms", newBackoff);
            }

        } catch (Exception e) {
            LOG.error("Unexpected error during poll cycle: {}", e.getMessage(), e);
            onPollFailure();
        }
    }

    private void onPollFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpen.set(true);
            circuitOpenedAt.set(System.currentTimeMillis());
            LOG.error("Circuit breaker OPEN after {} consecutive failures. "
                    + "Cooldown: {}ms", failures, CIRCUIT_BREAKER_COOLDOWN_MS);
        }
    }

    private void submitJob(Job job) {
        virtualThreadExecutor.submit(() -> {
            try {
                executeWithTimeout(job);
            } finally {
                activeJobCount.decrementAndGet();
                concurrencyLimit.release();
            }
        });
    }

    private void executeWithTimeout(Job job) {
        String jobId = job.id();
        try {
            jobQueue.updateStatus(jobId, JobStatus.RUNNING);

            Future<JobExecutor.JobResult> resultFuture = virtualThreadExecutor.submit(
                    () -> jobExecutor.execute(job, config.getWorkerId()));

            long timeoutSeconds = job.timeoutSeconds() > 0 ? job.timeoutSeconds() : 3600;
            JobExecutor.JobResult result = resultFuture.get(timeoutSeconds, TimeUnit.SECONDS);

            if (result.status() == ExecutionStatus.PASSED) {
                jobQueue.updateStatus(jobId, JobStatus.SUCCEEDED);
                LOG.info("Job {} completed successfully in {} ms", jobId, result.durationMs());
            } else {
                handleFailure(job, null);
            }

        } catch (TimeoutException e) {
            LOG.error("Job {} timed out after {} seconds", jobId, job.timeoutSeconds());
            jobQueue.updateStatus(jobId, JobStatus.TIMED_OUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Job {} was interrupted", jobId);
            jobQueue.updateStatus(jobId, JobStatus.CANCELLED);
        } catch (Exception e) {
            LOG.error("Job {} failed with exception: {}", jobId, e.getMessage(), e);
            handleFailure(job, e);
        }
    }

    private void handleFailure(Job job, Exception cause) {
        if (job.retryCount() < job.maxRetries()) {
            LOG.info("Retrying job {} (attempt {}/{})", job.id(),
                    job.retryCount() + 1, job.maxRetries());

            Job retryJob = new Job(
                    job.id(), job.pipelineName(), job.runId(), job.stageName(),
                    JobStatus.QUEUED, null, job.pipelineYaml(),
                    job.variables(), job.secrets(), job.priority(),
                    job.maxRetries(), job.retryCount() + 1, job.createdAt(),
                    null, null, job.timeoutSeconds());

            jobQueue.enqueue(retryJob);
        } else {
            jobQueue.updateStatus(job.id(), JobStatus.FAILED);
            LOG.error("Job {} failed permanently after {} retries",
                    job.id(), job.retryCount());
        }
    }

    /**
     * Returns the current number of actively executing jobs.
     */
    public int getActiveJobCount() {
        return activeJobCount.get();
    }

    /**
     * Sets the draining flag to stop accepting new jobs.
     */
    public void setDraining(boolean draining) {
        this.draining.set(draining);
    }

    /**
     * Returns whether the worker is in draining mode.
     */
    public boolean isDraining() {
        return draining.get();
    }

    /**
     * Returns whether the circuit breaker is currently open (halting polls due to failures).
     */
    public boolean isCircuitOpen() {
        return circuitOpen.get();
    }

    /**
     * Returns the current adaptive backoff in milliseconds (0 = polling aggressively).
     */
    public long getCurrentBackoffMs() {
        return currentBackoffMs.get();
    }

    /**
     * Shuts down the virtual thread executor, waiting for active jobs to complete.
     */
    public boolean shutdown(long timeoutSeconds) throws InterruptedException {
        draining.set(true);
        virtualThreadExecutor.shutdown();
        return virtualThreadExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
    }
}
