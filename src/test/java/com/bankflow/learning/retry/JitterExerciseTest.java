package com.bankflow.learning.retry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JitterExerciseTest {

    @Test
    void zeroDelay_shouldReturnZero() {
        assertEquals(0, JitterExercise.fullJitter(0));
    }

    @Test
    void negativeDelay_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JitterExercise.fullJitter(-1)
        );
    }

    @Test
    void jitter_shouldStayWithinBounds() {
        for (int i = 0; i < 1000; i++) {
            long delay = JitterExercise.fullJitter(800);

            assertTrue(delay >= 0);
            assertTrue(delay <= 800);
        }
    }

    @Test
    void maxLongDelay_shouldNotOverflow() {
        for (int i = 0; i < 1000; i++) {
            long delay = JitterExercise.fullJitter(Long.MAX_VALUE);

            assertTrue(delay >= 0);
            assertTrue(delay <= Long.MAX_VALUE);
        }
    }
}
