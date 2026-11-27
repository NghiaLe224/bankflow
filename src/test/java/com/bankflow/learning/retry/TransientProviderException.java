package com.bankflow.learning.retry;

public class TransientProviderException extends RuntimeException{
    public TransientProviderException(String message) {
        super(message);
    }
}
