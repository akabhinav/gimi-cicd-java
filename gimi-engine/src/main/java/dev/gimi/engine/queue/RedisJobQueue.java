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
import redis.clients.jedis.resps.Tuple;

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
 * <p>Queue entries use {@code gimi:jobs:queue} as a sorted set keyed by priority score.
 * Job data is stored in {@code gimi:jobs:{id}} hashes. Heartbeats are tracked via
 * {@code gimi:jobs:{id}:heartbeat} keys with a TTL.
 */
public final class RedisJobQueue implements JobQueue {

    private static final Logger LOG = LoggerFactory.getLogger(RedisJobQueue.class);

    private static final String QUEUE_KEY = "gimi:jobs:queue";
    private static final String JOB_KEY_PREFIX = "gimi:jobs:";
    private static final String HEARTBEAT_SUFFIX = ":heartbeat";
    private static final String RUN_INDEX_PREFIX = "gimi:jobs:run:";
    private static final int HEARTBEAT_TTL_SECONDS = 120;

    /** Lua script to atomically dequeue and assign a job. */
    private static final String DEQUEUE_LUA = """
            local result = redis.call('ZPOPMIN', KEYS[1], 1)
            if #result == 0 then
                return nil
            end
            local jobId = result[1]
            local jobKey = ARGV[1] .. jobId
            local workerId = ARGV[2]
            redis.call('HSET', jobKey, 'status', 'ASSIGNED', 'worker_id', workerId)
            return jobId
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

            jedis.hset(jobKey, "data", json);
            jedis.hset(jobKey, "status", JobStatus.QUEUED.name());
            jedis.zadd(QUEUE_KEY, job.priority(), job.id());
            jedis.sadd(RUN_INDEX_PREFIX + job.runId(), job.id());

            LOG.debug("Enqueued job: id={}, priority={}, pipeline={}", job.id(), job.priority(), job.pipelineName());
        }
    }

    @Override
    public Optional<Job> dequeue(String workerId, Set<String> labels) {
        Objects.requireNonNull(workerId, "workerId must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(DEQUEUE_LUA, 1, QUEUE_KEY, JOB_KEY_PREFIX, workerId);

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
            // Return the job with ASSIGNED status and workerId set
            Job assigned = new Job(
                    job.id(), job.pipelineName(), job.runId(), job.stageName(),
                    JobStatus.ASSIGNED, workerId, job.pipelineYaml(),
                    job.variables(), job.secrets(), job.priority(),
                    job.maxRetries(), job.retryCount(), job.createdAt(),
                    Instant.now(), null, job.timeoutSeconds()
            );

            jedis.hset(jobKey, "data", toJson(assigned));
            jedis.setex(jobKey + HEARTBEAT_SUFFIX, HEARTBEAT_TTL_SECONDS, Instant.now().toString());

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

            jedis.hset(jobKey, "data", toJson(updated));
            jedis.hset(jobKey, "status", status.name());

            if (status.isTerminal()) {
                jedis.del(jobKey + HEARTBEAT_SUFFIX);
            }

            LOG.debug("Updated job {} to status {}", jobId, status);
        }
    }

    @Override
    public void heartbeat(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(JOB_KEY_PREFIX + jobId + HEARTBEAT_SUFFIX, HEARTBEAT_TTL_SECONDS, Instant.now().toString());
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

            List<Job> jobs = new ArrayList<>(jobIds.size());
            for (String jobId : jobIds) {
                String json = jedis.hget(JOB_KEY_PREFIX + jobId, "data");
                if (json != null) {
                    jobs.add(fromJson(json));
                }
            }
            return jobs;
        }
    }

    @Override
    public List<Job> getStaleJobs(Duration threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            List<Job> stale = new ArrayList<>();
            String cursor = "0";

            do {
                var scanResult = jedis.scan(cursor, new redis.clients.jedis.params.ScanParams()
                        .match(JOB_KEY_PREFIX + "*")
                        .count(100));
                cursor = scanResult.getCursor();

                for (String key : scanResult.getResult()) {
                    if (key.endsWith(HEARTBEAT_SUFFIX) || key.startsWith(RUN_INDEX_PREFIX)) {
                        continue;
                    }

                    String status = jedis.hget(key, "status");
                    if ("RUNNING".equals(status) || "ASSIGNED".equals(status)) {
                        String heartbeatKey = key + HEARTBEAT_SUFFIX;
                        if (!jedis.exists(heartbeatKey)) {
                            String json = jedis.hget(key, "data");
                            if (json != null) {
                                stale.add(fromJson(json));
                            }
                        }
                    }
                }
            } while (!"0".equals(cursor));

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
