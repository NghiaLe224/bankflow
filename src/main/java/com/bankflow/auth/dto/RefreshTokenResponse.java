package com.bankflow.auth.dto;

public record RefreshTokenResponse(
        String accessToken,
        String tokenType
) {
}
