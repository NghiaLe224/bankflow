package com.bankflow.common.exception;

public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException(Long accountId) {
        super("Account " + accountId + " is not active");
    }
}
