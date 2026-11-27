package com.bankflow.payment.resilience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ThreadSleeperTest {

    @Test
    void sleep_zeroMillis_shouldComplete() {
        Sleeper sleeper = new ThreadSleeper();

        assertDoesNotThrow(() -> sleeper.sleep(0));
    }
}
