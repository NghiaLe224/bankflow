
package com.bankflow.payment.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryExecutorTest {

    @Test
    void twoTransientFailures_thenSuccess_shouldUseCorrectDelays()
            throws InterruptedException {

        AtomicInteger attempts = new AtomicInteger();

        RetryDelayStrategy delayStrategy =
                retryNumber -> retryNumber * 200L;

        Sleeper sleeper = mock(Sleeper.class);

        RetryExecutor executor =
                new RetryExecutor(3, delayStrategy, sleeper);

        String result = executor.execute(
                () -> {
                    int current = attempts.incrementAndGet();

                    if (current <= 2) {
                        throw new IllegalStateException(
                                "Temporary failure"
                        );
                    }

                    return "SUCCESS";
                },
                exception -> exception instanceof IllegalStateException
        );

        assertEquals("SUCCESS", result);
        assertEquals(3, attempts.get());

        verify(sleeper, times(1)).sleep(200L);
        verify(sleeper, times(1)).sleep(400L);
        verifyNoMoreInteractions(sleeper);
    }


    @Test
    void successOnFirstAttempt_shouldNotSleep() {
        AtomicInteger attempts = new AtomicInteger();

        RetryDelayStrategy delayStrategy = mock(RetryDelayStrategy.class);
        Sleeper sleeper = mock(Sleeper.class);

        RetryExecutor executor =
                new RetryExecutor(3, delayStrategy, sleeper);

        String result = executor.execute(
                () -> {
                    attempts.incrementAndGet();
                    return "SUCCESS";
                },
                exception -> true
        );

        assertEquals("SUCCESS", result);
        assertEquals(1, attempts.get());

        verifyNoInteractions(delayStrategy, sleeper);
    }

    @Test
    void nonRetryableException_shouldStopImmediately() {
        AtomicInteger attempts = new AtomicInteger();

        RetryDelayStrategy delayStrategy = mock(RetryDelayStrategy.class);
        Sleeper sleeper = mock(Sleeper.class);

        RetryExecutor executor =
                new RetryExecutor(3, delayStrategy, sleeper);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(
                        () -> {
                            attempts.incrementAndGet();
                            throw new IllegalArgumentException("Invalid request");
                        },
                        error -> error instanceof IllegalStateException
                )
        );

        assertEquals("Invalid request", exception.getMessage());
        assertEquals(1, attempts.get());

        verifyNoInteractions(delayStrategy, sleeper);
    }

    @Test
    void retryableException_exhaustedAttempts_shouldThrowLastException()
            throws InterruptedException {

        AtomicInteger attempts = new AtomicInteger();

        RetryDelayStrategy delayStrategy =
                retryNumber -> retryNumber * 200L;

        Sleeper sleeper = mock(Sleeper.class);

        RetryExecutor executor =
                new RetryExecutor(3, delayStrategy, sleeper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(
                        () -> {
                            int current = attempts.incrementAndGet();
                            throw new IllegalStateException(
                                    "Failure at attempt " + current
                            );
                        },
                        error -> error instanceof IllegalStateException
                )
        );

        assertEquals("Failure at attempt 3", exception.getMessage());
        assertEquals(3, attempts.get());

        verify(sleeper).sleep(200L);
        verify(sleeper).sleep(400L);
        verifyNoMoreInteractions(sleeper);
    }

    @Test
    void invalidConstructorArguments_shouldReject() {
        RetryDelayStrategy delayStrategy = retryNumber -> 200L;
        Sleeper sleeper = mock(Sleeper.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryExecutor(0, delayStrategy, sleeper)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryExecutor(-1, delayStrategy, sleeper)
        );

        assertThrows(
                NullPointerException.class,
                () -> new RetryExecutor(3, null, sleeper)
        );

        assertThrows(
                NullPointerException.class,
                () -> new RetryExecutor(3, delayStrategy, null)
        );
    }

    @Test
    void nullOperationOrPredicate_shouldReject() {
        RetryExecutor executor = new RetryExecutor(
                3,
                retryNumber -> 200L,
                millis -> {}
        );

        assertThrows(
                NullPointerException.class,
                () -> executor.execute(
                        null,
                        error -> true
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> executor.execute(
                        () -> "SUCCESS",
                        null
                )
        );
    }

    @Test
    void negativeDelay_shouldThrowWithoutSleeping() {
        AtomicInteger attempts = new AtomicInteger();

        RetryDelayStrategy delayStrategy =
                retryNumber -> -100L;

        Sleeper sleeper = mock(Sleeper.class);

        RetryExecutor executor =
                new RetryExecutor(3, delayStrategy, sleeper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> executor.execute(
                        () -> {
                            attempts.incrementAndGet();
                            throw new IllegalStateException("Temporary failure");
                        },
                        error -> true
                )
        );

        assertEquals("Retry delay cannot be negative", exception.getMessage());
        assertEquals(1, attempts.get());

        verifyNoInteractions(sleeper);
    }

    @Test
    void interruptedSleep_shouldRestoreInterruptFlagAndStopRetry()
            throws InterruptedException {

        AtomicInteger attempts = new AtomicInteger();

        RetryDelayStrategy delayStrategy =
                retryNumber -> 200L;

        Sleeper sleeper = mock(Sleeper.class);

        doThrow(new InterruptedException("Sleep interrupted"))
                .when(sleeper)
                .sleep(200L);

        RetryExecutor executor =
                new RetryExecutor(3, delayStrategy, sleeper);

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> executor.execute(
                            () -> {
                                attempts.incrementAndGet();
                                throw new IllegalStateException(
                                        "Temporary failure"
                                );
                            },
                            error -> true
                    )
            );

            assertEquals("retry interrupted", exception.getMessage());

            assertInstanceOf(
                    InterruptedException.class,
                    exception.getCause()
            );

            assertEquals(1, attempts.get());

            assertTrue(Thread.currentThread().isInterrupted());

            verify(sleeper, times(1)).sleep(200L);
            verifyNoMoreInteractions(sleeper);

        } finally {
            Thread.interrupted();
        }
    }

}
