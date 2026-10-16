package com.bankflow.security.exception;

public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException() {
        super("You are not allowed to access this resource");
    }

}
