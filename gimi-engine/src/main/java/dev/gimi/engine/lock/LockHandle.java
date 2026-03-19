package dev.gimi.engine.lock;

/**
 * Represents an acquired distributed lock.
 *
 * @param lockName     the name of the lock
 * @param lockValue    the unique value used to identify the lock owner (typically a UUID)
 * @param acquiredAtMs the wall-clock time in milliseconds when the lock was acquired
 */
public record LockHandle(String lockName, String lockValue, long acquiredAtMs) {}
