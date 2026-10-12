package com.bankflow.auth.dto;

public record LoginResponse(
        Long id,
        String fullName,
        String email,
        String accessToken,
        String refreshToken,
        String tokenType
) {
}
