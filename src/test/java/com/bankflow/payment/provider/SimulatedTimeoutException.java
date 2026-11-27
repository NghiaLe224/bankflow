package com.bankflow.payment.provider;

public class SimulatedTimeoutException extends RuntimeException {

    public SimulatedTimeoutException(String message) {
        super(message);
    }
}