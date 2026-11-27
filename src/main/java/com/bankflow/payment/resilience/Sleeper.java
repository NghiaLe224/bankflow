package com.bankflow.payment.resilience;

@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
