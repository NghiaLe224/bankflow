
package com.bankflow.learning.retry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffExerciseTest {

    @Test
    void firstRetry_shouldReturnBaseDelay() {
        assertEquals(
                200,
                ExponentialBackoffExercise.calculateDelay(1, 200, 2000)
        );
    }

    @Test
    void retries_shouldDoubleDelay() {
        assertEquals(
                400,
                ExponentialBackoffExercise.calculateDelay(2, 200, 2000)
        );

        assertEquals(
                800,
                ExponentialBackoffExercise.calculateDelay(3, 200, 2000)
        );

        assertEquals(
                1600,
                ExponentialBackoffExercise.calculateDelay(4, 200, 2000)
        );
    }

    @Test
    void delay_shouldNotExceedMaxDelay() {
        assertEquals(
                2000,
                ExponentialBackoffExercise.calculateDelay(5, 200, 2000)
        );

        assertEquals(
                2000,
                ExponentialBackoffExercise.calculateDelay(30, 200, 2000)
        );
    }

    @Test
    void delay_shouldHandleOddMaxDelay() {
        assertEquals(
                999,
                ExponentialBackoffExercise.calculateDelay(2, 500, 999)
        );
    }

    @Test
    void delay_shouldAvoidLongOverflow() {
        assertEquals(
                Long.MAX_VALUE,
                ExponentialBackoffExercise.calculateDelay(
                        100,
                        Long.MAX_VALUE / 2,
                        Long.MAX_VALUE
                )
        );
    }

    @Test
    void invalidRetryNumber_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExponentialBackoffExercise.calculateDelay(0, 200, 2000)
        );
    }

    @Test
    void invalidBaseDelay_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExponentialBackoffExercise.calculateDelay(1, 0, 2000)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ExponentialBackoffExercise.calculateDelay(1, -100, 2000)
        );
    }

    @Test
    void invalidMaxDelay_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExponentialBackoffExercise.calculateDelay(1, 500, 100)
        );
    }

    @Test
    void maxDelayEqualBaseDelay_shouldWork() {
        assertEquals(
                500,
                ExponentialBackoffExercise.calculateDelay(10, 500, 500)
        );
    }
}
