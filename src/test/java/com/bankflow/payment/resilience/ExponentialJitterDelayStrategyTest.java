
package com.bankflow.payment.resilience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialJitterDelayStrategyTest {

    @Test
    void firstRetry_shouldStayWithinBaseDelay() {
        RetryDelayStrategy strategy =
                new ExponentialJitterDelayStrategy(200, 2000);

        for (int i = 0; i < 1000; i++) {
            long actualDelay = strategy.calculateDelay(1);

            assertTrue(actualDelay >= 0);
            assertTrue(actualDelay <= 200);
        }
    }

    @Test
    void thirdRetry_shouldStayWithinExponentialDelay() {
        RetryDelayStrategy strategy =
                new ExponentialJitterDelayStrategy(200, 2000);

        for (int i = 0; i < 1000; i++) {
            long actualDelay = strategy.calculateDelay(3);

            assertTrue(actualDelay >= 0);
            assertTrue(actualDelay <= 800);
        }
    }

    @Test
    void largeRetryNumber_shouldRespectMaxDelay() {
        RetryDelayStrategy strategy =
                new ExponentialJitterDelayStrategy(200, 2000);

        for (int i = 0; i < 1000; i++) {
            long actualDelay = strategy.calculateDelay(1000);

            assertTrue(actualDelay >= 0);
            assertTrue(actualDelay <= 2000);
        }
    }

    @Test
    void maxLongDelay_shouldNotOverflow() {
        RetryDelayStrategy strategy =
                new ExponentialJitterDelayStrategy(
                        Long.MAX_VALUE / 2,
                        Long.MAX_VALUE
                );

        for (int i = 0; i < 1000; i++) {
            long actualDelay = strategy.calculateDelay(100);

            assertTrue(actualDelay >= 0);
        }
    }

    @Test
    void invalidRetryNumber_shouldReject() {
        RetryDelayStrategy strategy =
                new ExponentialJitterDelayStrategy(200, 2000);

        assertThrows(
                IllegalArgumentException.class,
                () -> strategy.calculateDelay(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> strategy.calculateDelay(-1)
        );
    }

    @Test
    void invalidConstructorArguments_shouldReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialJitterDelayStrategy(0, 2000)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialJitterDelayStrategy(-100, 2000)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExponentialJitterDelayStrategy(500, 100)
        );
    }
}
