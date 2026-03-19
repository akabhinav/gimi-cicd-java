package dev.gimi.engine.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * Abstraction for a distributed lock used to coordinate work across multiple
 * server instances (e.g., cron scheduling, singleton tasks).
 */
public interface DistributedLock {

    /**
     * Attempts to acquire the named lock with the given time-to-live.
     *
     * @param lockName the unique name identifying the lock
     * @param ttl      the maximum duration the lock should be held before automatic expiry
     * @return a {@link LockHandle} if the lock was acquired, or empty if it is already held
     */
    Optional<LockHandle> tryAcquire(String lockName, Duration ttl);

    /**
     * Releases a previously acquired lock. Only succeeds if the handle's value matches
     * the current lock value, preventing accidental release of locks held by other owners.
     *
     * @param handle the lock handle returned from {@link #tryAcquire}
     */
    void release(LockHandle handle);

    /**
     * Checks whether the named lock is currently held.
     *
     * @param lockName the lock name to check
     * @return {@code true} if the lock is currently held
     */
    boolean isLocked(String lockName);
}
