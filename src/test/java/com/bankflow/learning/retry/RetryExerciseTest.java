
package com.bankflow.learning.retry;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryExerciseTest {

    @Test
    void retry_successOnFirstAttempt_shouldCallOnce() {
        AtomicInteger attempts = new AtomicInteger();

        String result = RetryExercise.retry(3, () -> {
            attempts.incrementAndGet();
            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void retry_twoTransientFailures_thenSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        String result = RetryExercise.retry(3, () -> {
            int current = attempts.incrementAndGet();

            if (current <= 2) {
                throw new TransientProviderException(
                        "Temporary failure"
                );
            }

            return "SUCCESS";
        });

        assertEquals("SUCCESS", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void retry_exhaustedAttempts_shouldThrowLastException() {
        AtomicInteger attempts = new AtomicInteger();

        TransientProviderException exception = assertThrows(
                TransientProviderException.class,
                () -> RetryExercise.retry(3, () -> {
                    attempts.incrementAndGet();

                    throw new TransientProviderException(
                            "Provider unavailable"
                    );
                })
        );

        assertEquals("Provider unavailable", exception.getMessage());
        assertEquals(3, attempts.get());
    }

    @Test
    void retry_nonRetryableException_shouldStopImmediately() {
        AtomicInteger attempts = new AtomicInteger();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RetryExercise.retry(3, () -> {
                    attempts.incrementAndGet();

                    throw new IllegalArgumentException(
                            "Invalid amount"
                    );
                })
        );

        assertEquals("Invalid amount", exception.getMessage());
        assertEquals(1, attempts.get());
    }

    @Test
    void retry_transientThenNonRetryable_shouldStopAtSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                IllegalArgumentException.class,
                () -> RetryExercise.retry(3, () -> {
                    int current = attempts.incrementAndGet();

                    if (current == 1) {
                        throw new TransientProviderException(
                                "Temporary failure"
                        );
                    }

                    throw new IllegalArgumentException(
                            "Invalid request"
                    );
                })
        );

        assertEquals(2, attempts.get());
    }

    @Test
    void retry_invalidMaxAttempts_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryExercise.retry(0, () -> "SUCCESS")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RetryExercise.retry(-1, () -> "SUCCESS")
        );
    }

    @Test
    void retry_nullOperation_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryExercise.retry(3, null)
        );
    }
}
