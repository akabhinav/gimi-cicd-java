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

/**
 * Core worker component that polls the job queue for available work and
 * submits jobs for parallel execution on virtual threads.
 *
 * <p>Uses a {@link Semaphore} to limit concurrency to the configured
 * {@code maxConcurrentJobs}. Each polled job is submitted to a virtual
 * thread executor and wrapped with timeout handling and retry logic.
 */
@Component
public class JobPoller {

    private static final Logger LOG = LoggerFactory.getLogger(JobPoller.class);

    private final JobQueue jobQueue;
    private final JobExecutor jobExecutor;
    private final WorkerConfig config;
    private final Semaphore concurrencyLimit;
    private final ExecutorService virtualThreadExecutor;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public JobPoller(JobQueue jobQueue, JobExecutor jobExecutor, WorkerConfig config) {
        this.jobQueue = jobQueue;
        this.jobExecutor = jobExecutor;
        this.config = config;
        this.concurrencyLimit = new Semaphore(config.getMaxConcurrentJobs());
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Periodically polls the job queue for available work. Respects the configured
     * poll interval and concurrency limits.
     */
    @Scheduled(fixedDelayString = "${gimi.worker.poll-interval-ms:1000}")
    public void poll() {
        if (draining.get()) {
            LOG.debug("Worker is draining, skipping poll");
            return;
        }

        if (!concurrencyLimit.tryAcquire()) {
            LOG.debug("All job slots occupied ({}/{}), skipping poll",
                    activeJobCount.get(), config.getMaxConcurrentJobs());
            return;
        }

        try {
            Optional<Job> maybeJob = jobQueue.dequeue(
                    config.getWorkerId(), config.getLabels());

            if (maybeJob.isEmpty()) {
                concurrencyLimit.release();
                return;
            }

            Job job = maybeJob.get();
            LOG.info("Dequeued job: id={}, pipeline={}, stage={}",
                    job.id(), job.pipelineName(), job.stageName());

            activeJobCount.incrementAndGet();
            submitJob(job);

        } catch (Exception e) {
            concurrencyLimit.release();
            LOG.error("Error during job polling: {}", e.getMessage(), e);
        }
    }

    private void submitJob(Job job) {
        Future<?> future = virtualThreadExecutor.submit(() -> {
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
     *
     * @return the active job count
     */
    public int getActiveJobCount() {
        return activeJobCount.get();
    }

    /**
     * Sets the draining flag to stop accepting new jobs.
     *
     * @param draining {@code true} to stop polling for new jobs
     */
    public void setDraining(boolean draining) {
        this.draining.set(draining);
    }

    /**
     * Returns whether the worker is in draining mode.
     *
     * @return {@code true} if draining
     */
    public boolean isDraining() {
        return draining.get();
    }

    /**
     * Shuts down the virtual thread executor, waiting for active jobs to complete.
     *
     * @param timeoutSeconds the maximum time to wait for active jobs to finish
     * @return {@code true} if all jobs completed within the timeout
     * @throws InterruptedException if the wait is interrupted
     */
    public boolean shutdown(long timeoutSeconds) throws InterruptedException {
        draining.set(true);
        virtualThreadExecutor.shutdown();
        return virtualThreadExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
    }
}
