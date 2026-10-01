package com.bankflow.common.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer " + id + " was not found");
    }
}
