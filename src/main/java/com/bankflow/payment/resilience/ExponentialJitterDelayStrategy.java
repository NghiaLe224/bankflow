package com.bankflow.payment.resilience;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialJitterDelayStrategy implements RetryDelayStrategy {

    private final long baseDelayMillis;
    private final long maxDelayMillis;

    public ExponentialJitterDelayStrategy(
            long baseDelayMillis,
            long maxDelayMillis
    ) {
        if (baseDelayMillis <= 0) {
            throw new IllegalArgumentException("base delay must be greater than 0");
        }

        if (maxDelayMillis < baseDelayMillis) {
            throw new IllegalArgumentException("max delay must be equal or greater than base delay");
        }
        this.baseDelayMillis = baseDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
    }

    @Override
    public long calculateDelay(int retryNumber) {
        if (retryNumber < 1) {
            throw new IllegalArgumentException("retry number must be greater than zero");
        }

        long delay = baseDelayMillis;

        for (int i = 1; i < retryNumber; ++i) {
            if (delay > maxDelayMillis / 2) {
                delay = maxDelayMillis;
                break;
            }
            delay *= 2;
        }

        long cappedDelayMillis = Math.min(delay, maxDelayMillis);

        if (cappedDelayMillis == Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE;
        }

        return ThreadLocalRandom.current().nextLong(cappedDelayMillis + 1);
    }
}
