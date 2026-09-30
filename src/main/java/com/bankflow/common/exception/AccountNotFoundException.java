package com.bankflow.common.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Account " + id + " was not found");
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account number " + accountNumber + " was not found");
    }
}
