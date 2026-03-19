package dev.gimi.engine.queue;

import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Distributed job queue for scheduling and assigning pipeline work units to worker nodes.
 */
public interface JobQueue {

    /**
     * Enqueues a job for execution.
     *
     * @param job the job to enqueue
     */
    void enqueue(Job job);

    /**
     * Atomically dequeues the highest-priority job matching the worker's labels.
     *
     * @param workerId the ID of the worker requesting work
     * @param labels   the set of labels the worker supports
     * @return the dequeued job, or empty if no matching jobs are available
     */
    Optional<Job> dequeue(String workerId, Set<String> labels);

    /**
     * Updates the status of a job.
     *
     * @param jobId  the job ID
     * @param status the new status
     */
    void updateStatus(String jobId, JobStatus status);

    /**
     * Records a heartbeat for a running job, indicating it is still active.
     *
     * @param jobId the job ID
     */
    void heartbeat(String jobId);

    /**
     * Retrieves a job by its ID.
     *
     * @param jobId the job ID
     * @return the job, or empty if not found
     */
    Optional<Job> getJob(String jobId);

    /**
     * Returns all jobs associated with a pipeline run.
     *
     * @param runId the pipeline run ID
     * @return list of jobs for the run
     */
    List<Job> getJobsByRun(String runId);

    /**
     * Returns jobs that have not sent a heartbeat within the given threshold,
     * indicating they may be stale or abandoned.
     *
     * @param threshold the maximum allowed duration since the last heartbeat
     * @return list of stale jobs
     */
    List<Job> getStaleJobs(Duration threshold);

    /**
     * Returns the current number of queued jobs.
     *
     * @return the queue size
     */
    long queueSize();
}
