package com.bankflow.security.jwt.exception;

public class InvalidJwtTokenException extends RuntimeException{

    public InvalidJwtTokenException() {
        super("Invalid Token");
    }
}
