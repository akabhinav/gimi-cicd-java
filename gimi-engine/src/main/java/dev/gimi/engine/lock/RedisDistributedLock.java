package dev.gimi.engine.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed implementation of {@link DistributedLock} using the SET NX EX pattern.
 *
 * <p>Lock acquisition uses {@code SET key value NX EX ttl} for atomic compare-and-set.
 * Lock release uses a Lua script to atomically verify the lock value before deletion,
 * preventing accidental release of locks owned by other processes.
 */
public final class RedisDistributedLock implements DistributedLock {

    private static final Logger LOG = LoggerFactory.getLogger(RedisDistributedLock.class);

    private static final String LOCK_PREFIX = "gimi:lock:";

    /**
     * Lua script that atomically checks the lock value and deletes the key only if it matches.
     * This prevents a client from releasing a lock that has already expired and been re-acquired
     * by another client.
     */
    private static final String RELEASE_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """;

    private final JedisPool jedisPool;

    /**
     * Creates a new Redis-backed distributed lock.
     *
     * @param jedisPool the Jedis connection pool
     */
    public RedisDistributedLock(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
    }

    @Override
    public Optional<LockHandle> tryAcquire(String lockName, Duration ttl) {
        Objects.requireNonNull(lockName, "lockName must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        String key = LOCK_PREFIX + lockName;
        String value = UUID.randomUUID().toString();
        long ttlSeconds = Math.max(ttl.toSeconds(), 1);

        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, value, SetParams.setParams().nx().ex(ttlSeconds));

            if ("OK".equals(result)) {
                LOG.debug("Acquired lock: name={}, value={}, ttl={}s", lockName, value, ttlSeconds);
                return Optional.of(new LockHandle(lockName, value, System.currentTimeMillis()));
            }

            LOG.debug("Failed to acquire lock: name={} (already held)", lockName);
            return Optional.empty();
        }
    }

    @Override
    public void release(LockHandle handle) {
        Objects.requireNonNull(handle, "handle must not be null");

        String key = LOCK_PREFIX + handle.lockName();

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(RELEASE_LUA, 1, key, handle.lockValue());

            if (Long.valueOf(1L).equals(result)) {
                LOG.debug("Released lock: name={}, value={}", handle.lockName(), handle.lockValue());
            } else {
                LOG.warn("Lock release failed (not owner or expired): name={}, value={}",
                        handle.lockName(), handle.lockValue());
            }
        }
    }

    @Override
    public boolean isLocked(String lockName) {
        Objects.requireNonNull(lockName, "lockName must not be null");

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(LOCK_PREFIX + lockName);
        }
    }
}
