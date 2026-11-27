package com.bankflow.learning.retry;

public class ExponentialBackoffExercise {

    public static long calculateDelay(
            int retryNumber,
            long baseDelayMillis,
            long maxDelayMillis
    ) {
        if (retryNumber < 1) {
            throw new IllegalArgumentException("retry number must be equal or greater than 1");
        }

        if (baseDelayMillis <= 0) {
            throw new IllegalArgumentException("base delay must be greater than 0");
        }

        if (maxDelayMillis < baseDelayMillis) {
            throw new IllegalArgumentException("max delay must be equal or greater than base delay");
        }

        long delay = baseDelayMillis;

        for (int i = 1; i < retryNumber; ++i) {
            if (delay > maxDelayMillis / 2) {
                return maxDelayMillis;
            }
            delay = delay * 2;
        }

        return Math.min(delay, maxDelayMillis);
    }
}
