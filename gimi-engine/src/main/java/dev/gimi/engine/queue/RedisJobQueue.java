package dev.gimi.engine.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.gimi.core.model.Job;
import dev.gimi.core.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed implementation of {@link JobQueue} using sorted sets for priority ordering
 * and hashes for job data storage.
 *
 * <p>Optimized for 10,000+ concurrent pipeline executions:
 * <ul>
 *   <li>Atomic Lua-based dequeue with ZPOPMIN — no lock contention</li>
 *   <li>Redis pipelines for batched writes (enqueue, status updates)</li>
 *   <li>Indexed active-jobs sorted set for O(1) stale job detection instead of SCAN</li>
 *   <li>Heartbeat TTL-based staleness with sorted set scoring by timestamp</li>
 * </ul>
 *
 * <p>Queue entries use {@code gimi:jobs:queue} as a sorted set keyed by priority score.
 * Job data is stored in {@code gimi:jobs:{id}} hashes. Active jobs are tracked in
 * {@code gimi:jobs:active} sorted set scored by last heartbeat timestamp for efficient
 * stale-job detection.
 */
public final class RedisJobQueue implements JobQueue {

    private static final Logger LOG = LoggerFactory.getLogger(RedisJobQueue.class);

    private static final String QUEUE_KEY = "gimi:jobs:queue";
    private static final String ACTIVE_JOBS_KEY = "gimi:jobs:active";
    private static final String JOB_KEY_PREFIX = "gimi:jobs:";
    private static final String HEARTBEAT_SUFFIX = ":heartbeat";
    private static final String RUN_INDEX_PREFIX = "gimi:jobs:run:";
    private static final int HEARTBEAT_TTL_SECONDS = 120;
    private static final int JOB_DATA_TTL_SECONDS = 86_400; // 24h for completed job data

    /** Lua script to atomically dequeue and assign a job. */
    private static final String DEQUEUE_LUA = """
            local result = redis.call('ZPOPMIN', KEYS[1], 1)
            if #result == 0 then
                return nil
            end
            local jobId = result[1]
            local jobKey = ARGV[1] .. jobId
            local workerId = ARGV[2]
            local now = ARGV[3]
            redis.call('HSET', jobKey, 'status', 'ASSIGNED', 'worker_id', workerId)
            redis.call('ZADD', KEYS[2], tonumber(now), jobId)
            return jobId
            """;

    /** Lua script to atomically enqueue with pipeline — avoids round-trips. */
    private static final String ENQUEUE_LUA = """
            local jobKey = KEYS[1]
            local queueKey = KEYS[2]
            local runIndexKey = KEYS[3]
            local json = ARGV[1]
            local status = ARGV[2]
            local priority = tonumber(ARGV[3])
            local jobId = ARGV[4]
            redis.call('HSET', jobKey, 'data', json, 'status', status)
            redis.call('ZADD', queueKey, priority, jobId)
            redis.call('SADD', runIndexKey, jobId)
            return 1
            """;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Redis-backed job queue.
     *
     * @param jedisPool the Jedis connection pool
     */
    public RedisJobQueue(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void enqueue(Job job) {
        Objects.requireNonNull(job, "job must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            String jobKey = JOB_KEY_PREFIX + job.id();
            String json = toJson(job);

            // Single Lua call instead of 4 round-trips
            jedis.eval(ENQUEUE_LUA, 3,
                    jobKey, QUEUE_KEY, RUN_INDEX_PREFIX + job.runId(),
                    json, JobStatus.QUEUED.name(), String.valueOf(job.priority()), job.id());

            LOG.debug("Enqueued job: id={}, priority={}, pipeline={}", job.id(), job.priority(), job.pipelineName());
        }
    }

    @Override
    public Optional<Job> dequeue(String workerId, Set<String> labels) {
        Objects.requireNonNull(workerId, "workerId must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            String nowEpoch = String.valueOf(Instant.now().toEpochMilli());
            Object result = jedis.eval(DEQUEUE_LUA, 2,
                    QUEUE_KEY, ACTIVE_JOBS_KEY,
                    JOB_KEY_PREFIX, workerId, nowEpoch);

            if (result == null) {
                return Optional.empty();
            }

            String jobId = result.toString();
            String jobKey = JOB_KEY_PREFIX + jobId;
            String json = jedis.hget(jobKey, "data");

            if (json == null) {
                LOG.warn("Dequeued job {} but no data found in hash", jobId);
                return Optional.empty();
            }

            Job job = fromJson(json);
            Job assigned = new Job(
                    job.id(), job.pipelineName(), job.runId(), job.stageName(),
                    JobStatus.ASSIGNED, workerId, job.pipelineYaml(),
                    job.variables(), job.secrets(), job.priority(),
                    job.maxRetries(), job.retryCount(), job.createdAt(),
                    Instant.now(), null, job.timeoutSeconds()
            );

            // Batched write: update data + set heartbeat in one pipeline
            Pipeline pipe = jedis.pipelined();
            pipe.hset(jobKey, "data", toJson(assigned));
            pipe.setex(jobKey + HEARTBEAT_SUFFIX, HEARTBEAT_TTL_SECONDS, Instant.now().toString());
            pipe.sync();

            LOG.debug("Dequeued job: id={}, assignedTo={}", jobId, workerId);
            return Optional.of(assigned);
        }
    }

    @Override
    public void updateStatus(String jobId, JobStatus status) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            String jobKey = JOB_KEY_PREFIX + jobId;
            String json = jedis.hget(jobKey, "data");

            if (json == null) {
                LOG.warn("Cannot update status for unknown job: {}", jobId);
                return;
            }

            Job job = fromJson(json);
            Instant finishedAt = status.isTerminal() ? Instant.now() : job.finishedAt();
            Job updated = new Job(
                    job.id(), job.pipelineName(), job.runId(), job.stageName(),
                    status, job.workerId(), job.pipelineYaml(),
                    job.variables(), job.secrets(), job.priority(),
                    job.maxRetries(), job.retryCount(), job.createdAt(),
                    job.startedAt(), finishedAt, job.timeoutSeconds()
            );

            // Batched write with pipeline
            Pipeline pipe = jedis.pipelined();
            pipe.hset(jobKey, "data", toJson(updated));
            pipe.hset(jobKey, "status", status.name());

            if (status.isTerminal()) {
                pipe.del(jobKey + HEARTBEAT_SUFFIX);
                pipe.zrem(ACTIVE_JOBS_KEY, jobId);
                // Set TTL on completed job data to auto-cleanup
                pipe.expire(jobKey, JOB_DATA_TTL_SECONDS);
            }
            pipe.sync();

            LOG.debug("Updated job {} to status {}", jobId, status);
        }
    }

    @Override
    public void heartbeat(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            // Batched: update heartbeat key + active jobs score in one pipeline
            Pipeline pipe = jedis.pipelined();
            pipe.setex(JOB_KEY_PREFIX + jobId + HEARTBEAT_SUFFIX,
                    HEARTBEAT_TTL_SECONDS, Instant.now().toString());
            pipe.zadd(ACTIVE_JOBS_KEY, Instant.now().toEpochMilli(), jobId);
            pipe.sync();
        }
    }

    @Override
    public Optional<Job> getJob(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.hget(JOB_KEY_PREFIX + jobId, "data");
            return json == null ? Optional.empty() : Optional.of(fromJson(json));
        }
    }

    @Override
    public List<Job> getJobsByRun(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> jobIds = jedis.smembers(RUN_INDEX_PREFIX + runId);

            if (jobIds == null || jobIds.isEmpty()) {
                return Collections.emptyList();
            }

            // Batch fetch all job data using pipeline instead of N round-trips
            List<String> jobIdList = new ArrayList<>(jobIds);
            Pipeline pipe = jedis.pipelined();
            var responses = new ArrayList<redis.clients.jedis.Response<String>>(jobIdList.size());
            for (String jobId : jobIdList) {
                responses.add(pipe.hget(JOB_KEY_PREFIX + jobId, "data"));
            }
            pipe.sync();

            List<Job> result = new ArrayList<>(jobIdList.size());
            for (var response : responses) {
                String json = response.get();
                if (json != null) {
                    result.add(fromJson(json));
                }
            }
            return result;
        }
    }

    /**
     * Returns stale jobs using the {@code gimi:jobs:active} sorted set.
     *
     * <p>Instead of scanning all keys (O(N) on keyspace), queries the active jobs
     * sorted set for entries with heartbeat scores older than the threshold — O(log N + K)
     * where K is the number of stale jobs.
     */
    @Override
    public List<Job> getStaleJobs(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            long cutoffMs = Instant.now().minus(threshold).toEpochMilli();

            // Query active jobs scored before the cutoff — efficient range query
            List<String> staleIds = jedis.zrangeByScore(ACTIVE_JOBS_KEY, 0, cutoffMs)
                    .stream().toList();

            if (staleIds.isEmpty()) {
                return Collections.emptyList();
            }

            // Batch fetch stale job data
            Pipeline pipe = jedis.pipelined();
            var responses = new ArrayList<redis.clients.jedis.Response<String>>(staleIds.size());
            for (String jobId : staleIds) {
                responses.add(pipe.hget(JOB_KEY_PREFIX + jobId, "data"));
            }
            pipe.sync();

            List<Job> stale = new ArrayList<>();
            for (int i = 0; i < staleIds.size(); i++) {
                String json = responses.get(i).get();
                if (json != null) {
                    Job job = fromJson(json);
                    if (job.status() == JobStatus.RUNNING || job.status() == JobStatus.ASSIGNED) {
                        stale.add(job);
                    }
                }
            }
            return stale;
        }
    }

    @Override
    public long queueSize() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.zcard(QUEUE_KEY);
        }
    }

    private String toJson(Job job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize Job", e);
        }
    }

    private Job fromJson(String json) {
        try {
            return objectMapper.readValue(json, Job.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize Job", e);
        }
    }
}
