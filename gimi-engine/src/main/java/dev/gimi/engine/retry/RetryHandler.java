package dev.gimi.engine.retry;

import java.util.concurrent.Callable;

/**
 * Simple retry utility with exponential backoff.
 */
public final class RetryHandler {

    private RetryHandler() {
        // prevent instantiation
    }

    /**
     * Executes the given action, retrying on failure up to the specified number
     * of attempts with exponential backoff.
     *
     * <p>The delay between attempts doubles after each failure, starting from
     * {@code delayMs}. If all attempts are exhausted, the last exception is
     * rethrown as a {@link RuntimeException}.
     *
     * @param maxAttempts the maximum number of attempts (must be at least 1)
     * @param delayMs     the initial delay in milliseconds between retries
     * @param action      the action to execute
     * @param <T>         the return type of the action
     * @return the result of the action on a successful attempt
     * @throws RuntimeException if all attempts fail
     */
    public static <T> T withRetry(int maxAttempts, long delayMs, Callable<T> action) {
        Exception lastException = null;
        long currentDelay = delayMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(currentDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    currentDelay *= 2;
                }
            }
        }

        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException("Action failed after " + maxAttempts + " attempts", lastException);
    }
}
