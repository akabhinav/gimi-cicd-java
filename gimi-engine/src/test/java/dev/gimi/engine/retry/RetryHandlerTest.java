package dev.gimi.engine.retry;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHandlerTest {

    @Test
    void shouldSucceedOnFirstTry() {
        String result = RetryHandler.withRetry(3, 1, () -> "success");

        assertThat(result).isEqualTo("success");
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryHandler.withRetry(3, 1, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("fail");
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldRetryAndSucceedOnLastAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryHandler.withRetry(3, 1, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("fail");
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldExhaustRetriesAndThrowRuntimeException() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> RetryHandler.withRetry(3, 1, () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("always fails");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldExhaustRetriesAndWrapCheckedException() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> RetryHandler.withRetry(2, 1, () -> {
            attempts.incrementAndGet();
            throw new Exception("checked exception");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Action failed after 2 attempts")
                .hasCauseInstanceOf(Exception.class);

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldExecuteExactlyOnceWithSingleAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryHandler.withRetry(1, 1, () -> {
            attempts.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldFailImmediatelyWithSingleAttemptOnError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> RetryHandler.withRetry(1, 1, () -> {
            attempts.incrementAndGet();
            throw new RuntimeException("fail");
        }))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldRethrowOriginalRuntimeException() {
        IllegalStateException original = new IllegalStateException("original error");

        assertThatThrownBy(() -> RetryHandler.withRetry(1, 1, () -> {
            throw original;
        }))
                .isSameAs(original);
    }

    @Test
    void shouldReturnCorrectType() {
        int result = RetryHandler.withRetry(1, 1, () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void shouldCallActionExpectedNumberOfTimes() {
        AtomicInteger attempts = new AtomicInteger(0);

        try {
            RetryHandler.withRetry(5, 1, () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("fail");
            });
        } catch (RuntimeException ignored) {
        }

        assertThat(attempts.get()).isEqualTo(5);
    }
}
