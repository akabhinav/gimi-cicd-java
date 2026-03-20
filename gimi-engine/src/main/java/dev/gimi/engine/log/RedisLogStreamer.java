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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Redis-backed implementation of {@link LogStreamer} using a single multiplexed
 * pub/sub connection for all subscriptions.
 *
 * <p>Optimized for 10,000+ concurrent pipeline runs:
 * <ul>
 *   <li>Single shared Redis connection for all pub/sub via pattern subscribe</li>
 *   <li>One virtual thread manages the subscription — not one per run</li>
 *   <li>Listeners dispatched from a ConcurrentHashMap — O(1) routing</li>
 *   <li>Log entries batched via Redis pipeline for publish + persist</li>
 * </ul>
 *
 * <p>Log entries are appended to {@code gimi:logs:{runId}} lists with a 24-hour TTL.
 * Real-time subscribers receive entries via {@code gimi:logs:live:{runId}} pub/sub channels.
 */
public final class RedisLogStreamer implements LogStreamer {

    private static final Logger LOG = LoggerFactory.getLogger(RedisLogStreamer.class);

    private static final String LOG_KEY_PREFIX = "gimi:logs:";
    private static final String LIVE_CHANNEL_PREFIX = "gimi:logs:live:";
    private static final String LIVE_CHANNEL_PATTERN = "gimi:logs:live:*";
    private static final int LOG_TTL_SECONDS = 86_400; // 24 hours

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final Map<String, Consumer<LogEntry>> listeners = new ConcurrentHashMap<>();
    private final AtomicBoolean subscriberStarted = new AtomicBoolean(false);
    private volatile JedisPubSub sharedPubSub;
    private volatile Thread subscriberThread;

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
            // Batch: persist + publish + set TTL in one pipeline round-trip
            var pipe = jedis.pipelined();
            pipe.rpush(listKey, json);
            pipe.expire(listKey, LOG_TTL_SECONDS);
            pipe.publish(channel, json);
            pipe.sync();
        }

        LOG.debug("Published log entry: runId={}, stage={}, step={}",
                entry.runId(), entry.stageName(), entry.stepName());
    }

    @Override
    public void subscribe(String runId, Consumer<LogEntry> listener) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        listeners.put(runId, listener);
        ensureSubscriberRunning();
        LOG.debug("Subscribed to logs: runId={} (total active: {})", runId, listeners.size());
    }

    @Override
    public void unsubscribe(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        Consumer<LogEntry> removed = listeners.remove(runId);
        if (removed != null) {
            LOG.debug("Unsubscribed from logs: runId={} (remaining: {})", runId, listeners.size());
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

    /**
     * Ensures a single shared subscriber thread is running. Uses pattern subscribe
     * so one Redis connection handles ALL run subscriptions.
     */
    private void ensureSubscriberRunning() {
        if (subscriberStarted.compareAndSet(false, true)) {
            sharedPubSub = new JedisPubSub() {
                @Override
                public void onPMessage(String pattern, String channel, String message) {
                    // Extract runId from channel: "gimi:logs:live:{runId}"
                    String runId = channel.substring(LIVE_CHANNEL_PREFIX.length());
                    Consumer<LogEntry> listener = listeners.get(runId);
                    if (listener != null) {
                        try {
                            LogEntry entry = fromJson(message);
                            listener.accept(entry);
                        } catch (Exception e) {
                            LOG.error("Error processing log message on channel {}: {}",
                                    channel, e.getMessage(), e);
                        }
                    }
                }
            };

            subscriberThread = Thread.ofVirtual()
                    .name("log-subscriber-shared")
                    .start(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            try (Jedis jedis = jedisPool.getResource()) {
                                LOG.info("Starting shared log subscriber on pattern: {}", LIVE_CHANNEL_PATTERN);
                                jedis.psubscribe(sharedPubSub, LIVE_CHANNEL_PATTERN);
                            } catch (Exception e) {
                                if (Thread.currentThread().isInterrupted()) {
                                    LOG.debug("Shared log subscriber interrupted, shutting down");
                                    break;
                                }
                                LOG.error("Shared log subscriber disconnected, reconnecting in 1s", e);
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    });

            LOG.info("Shared log subscriber thread started (handles all run subscriptions)");
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
}
