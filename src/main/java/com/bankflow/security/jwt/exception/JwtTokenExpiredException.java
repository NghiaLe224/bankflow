package com.bankflow.security.jwt.exception;

public class JwtTokenExpiredException extends RuntimeException {
    public JwtTokenExpiredException() {
        super("Token has expired");
    }
}
