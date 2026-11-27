package com.bankflow.payment.resilience;

@FunctionalInterface
public interface RetryDelayStrategy {
    long calculateDelay(int retryNumber);
}
