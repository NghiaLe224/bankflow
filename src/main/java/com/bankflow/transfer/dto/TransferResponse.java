package com.bankflow.transfer.dto;

import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(

        @Schema(
                description = "Unique transfer identifier",
                example = "10"
        )
        Long id,

        @Schema(
                description = "Source account identifier",
                example = "1"
        )
        Long fromAccountId,

        @Schema(
                description = "Destination account identifier",
                example = "2"
        )
        Long toAccountId,

        @Schema(
                description = "Transfer amount",
                example = "500000.00"
        )
        BigDecimal amount,

        @Schema(
                description = "Currency used for the transfer",
                example = "VND"
        )
        Currency currency,

        @Schema(
                description = "Current transfer status",
                example = "COMPLETED"
        )
        TransferStatus status,

        @Schema(
                description = "Time when the transfer was created",
                example = "2026-10-03T17:09:33"
        )
        LocalDateTime createdAt
) {}
