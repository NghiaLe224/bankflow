package com.bankflow.common.exception;

public class TransientProviderException extends RuntimeException {
    public TransientProviderException(String message) {
        super(message);
    }

    public TransientProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
