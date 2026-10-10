package com.bankflow.auth.dto;

public record RegisterResponse(
        Long id,
        String fullName,
        String email
) {
}
