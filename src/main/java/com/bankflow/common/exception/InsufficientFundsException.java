package com.bankflow.common.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long id) {
        super("Insufficient funds for account " + id);
    }
}
