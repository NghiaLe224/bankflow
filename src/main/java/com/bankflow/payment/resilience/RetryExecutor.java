package com.bankflow.payment.resilience;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RetryExecutor {

    private final int maxAttempts;
    private final RetryDelayStrategy delayStrategy;
    private final Sleeper sleeper;

    public RetryExecutor(int maxAttempts, RetryDelayStrategy delayStrategy, Sleeper sleeper) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "maxAttempts must be greater than 0"
            );
        }
        this.maxAttempts = maxAttempts;
        this.delayStrategy = Objects.requireNonNull(
                delayStrategy,
                "delayStrategy cannot be null"
        );
        this.sleeper = Objects.requireNonNull(
                sleeper,
                "sleeper cannot be null"
        );
    }

    public <T> T execute(Supplier<T> operation, Predicate<RuntimeException> retryable) {
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(retryable, "retryable cannot be null");

        for (int i = 1; i <= maxAttempts; ++i) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                if (!retryable.test(e) || i == maxAttempts) {
                    throw e;
                }
                long delayMillis = delayStrategy.calculateDelay(i);
                if (delayMillis < 0) {
                    throw new IllegalStateException(
                            "Retry delay cannot be negative"
                    );
                }
                try {
                    sleeper.sleep(delayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("retry interrupted", ex);
                }
            }
        }
        throw new IllegalStateException("Unexpected retry state");
    }
}
