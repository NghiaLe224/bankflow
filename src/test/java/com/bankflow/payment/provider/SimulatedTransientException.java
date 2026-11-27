package com.bankflow.payment.provider;

public class SimulatedTransientException extends RuntimeException {

    public SimulatedTransientException(String message) {
        super(message);
    }
}