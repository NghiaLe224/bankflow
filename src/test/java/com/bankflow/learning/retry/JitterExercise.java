package com.bankflow.learning.retry;

import java.util.concurrent.ThreadLocalRandom;

public class JitterExercise {

    public static long fullJitter(long cappedDelayMillis) {
        if (cappedDelayMillis < 0) {
            throw new IllegalArgumentException("capped delay less than zero");
        }

        if (cappedDelayMillis == 0) {
            return 0;
        }

        if (cappedDelayMillis == Long.MAX_VALUE) {
            return ThreadLocalRandom.current()
                    .nextLong() & Long.MAX_VALUE;
        }

        return ThreadLocalRandom.current().nextLong(cappedDelayMillis + 1);

    }
}
