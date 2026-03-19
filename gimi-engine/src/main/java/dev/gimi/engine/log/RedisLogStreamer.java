package dev.gimi.engine.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.gimi.core.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Redis-backed implementation of {@link LogStreamer} using Redis pub/sub for real-time
 * delivery and Redis lists for persistent history.
 *
 * <p>Log entries are appended to {@code gimi:logs:{runId}} lists with a 24-hour TTL.
 * Real-time subscribers receive entries via {@code gimi:logs:live:{runId}} pub/sub channels.
 * Each subscription runs on a dedicated virtual thread.
 */
public final class RedisLogStreamer implements LogStreamer {

    private static final Logger LOG = LoggerFactory.getLogger(RedisLogStreamer.class);

    private static final String LOG_KEY_PREFIX = "gimi:logs:";
    private static final String LIVE_CHANNEL_PREFIX = "gimi:logs:live:";
    private static final int LOG_TTL_SECONDS = 86_400; // 24 hours

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final Map<String, SubscriptionHandle> subscriptions = new ConcurrentHashMap<>();

    /**
     * Creates a new Redis-backed log streamer.
     *
     * @param jedisPool the Jedis connection pool
     */
    public RedisLogStreamer(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void publish(LogEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        String json = toJson(entry);
        String listKey = LOG_KEY_PREFIX + entry.runId();
        String channel = LIVE_CHANNEL_PREFIX + entry.runId();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush(listKey, json);
            jedis.expire(listKey, LOG_TTL_SECONDS);
            jedis.publish(channel, json);
        }

        LOG.debug("Published log entry: runId={}, stage={}, step={}",
                entry.runId(), entry.stageName(), entry.stepName());
    }

    @Override
    public void subscribe(String runId, Consumer<LogEntry> listener) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        // Unsubscribe existing subscription for this runId if present
        unsubscribe(runId);

        String channel = LIVE_CHANNEL_PREFIX + runId;

        JedisPubSub pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String message) {
                try {
                    LogEntry entry = fromJson(message);
                    listener.accept(entry);
                } catch (Exception e) {
                    LOG.error("Error processing log message on channel {}: {}", ch, e.getMessage(), e);
                }
            }
        };

        Thread subscriberThread = Thread.ofVirtual()
                .name("log-subscriber-" + runId)
                .start(() -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.subscribe(pubSub, channel);
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            LOG.error("Log subscriber for runId={} terminated unexpectedly", runId, e);
                        }
                    }
                });

        subscriptions.put(runId, new SubscriptionHandle(pubSub, subscriberThread));
        LOG.debug("Subscribed to logs: runId={}", runId);
    }

    @Override
    public void unsubscribe(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        SubscriptionHandle handle = subscriptions.remove(runId);
        if (handle != null) {
            try {
                handle.pubSub().unsubscribe();
            } catch (Exception e) {
                LOG.debug("Error during unsubscribe for runId={}: {}", runId, e.getMessage());
            }
            handle.thread().interrupt();
            LOG.debug("Unsubscribed from logs: runId={}", runId);
        }
    }

    @Override
    public List<LogEntry> getHistory(String runId, int limit) {
        Objects.requireNonNull(runId, "runId must not be null");

        String listKey = LOG_KEY_PREFIX + runId;

        try (Jedis jedis = jedisPool.getResource()) {
            long len = jedis.llen(listKey);
            long start = Math.max(0, len - limit);
            List<String> entries = jedis.lrange(listKey, start, len - 1);

            if (entries == null || entries.isEmpty()) {
                return Collections.emptyList();
            }

            List<LogEntry> result = new ArrayList<>(entries.size());
            for (String json : entries) {
                result.add(fromJson(json));
            }
            return result;
        }
    }

    private String toJson(LogEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize LogEntry", e);
        }
    }

    private LogEntry fromJson(String json) {
        try {
            return objectMapper.readValue(json, LogEntry.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize LogEntry", e);
        }
    }

    private record SubscriptionHandle(JedisPubSub pubSub, Thread thread) {}
}
