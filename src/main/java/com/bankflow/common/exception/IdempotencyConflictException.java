package com.bankflow.common.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String s) {
        super(s);
    }
}
