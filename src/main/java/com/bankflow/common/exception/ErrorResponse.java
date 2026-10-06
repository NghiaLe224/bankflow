package com.bankflow.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        @Schema(
                description = "Machine-readable error code",
                example = "INVALID_TRANSFER_FILTER"
        )
        String code,

        @Schema(
                description = "Human-readable error message",
                example = "from must be before or equal to to"
        )
        String message,

        @Schema(
                description = "Time when the error occurred",
                example = "2026-10-05T13:20:00"
        )
        LocalDateTime timestamp,

        @Schema(
                description = "Field-level validation errors"
        )
        Map<String, String> errors
) {
    public ErrorResponse(String code, String message, LocalDateTime timestamp) {
        this(code, message, timestamp, null);
    }
}
