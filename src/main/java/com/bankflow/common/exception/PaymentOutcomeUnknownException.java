package com.bankflow.common.exception;

public class PaymentOutcomeUnknownException extends RuntimeException{
    public PaymentOutcomeUnknownException(String message) {
        super(message);
    }

    public PaymentOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
