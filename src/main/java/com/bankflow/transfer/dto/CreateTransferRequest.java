package com.bankflow.transfer.dto;

import com.bankflow.transfer.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransferRequest(
        @Schema(
                description = "Source account identifier",
                example = "1"
        )
        @NotNull(message = "From account id is required")
        @Positive(message = "From account id must be positive")
        Long fromAccountId,

        @Schema(
                description = "Destination account identifier",
                example = "2"
        )
        @NotNull(message = "To account id is required")
        @Positive(message = "To account id must be positive")
        Long toAccountId,

        @Schema(
                description = "Amount to transfer",
                example = "500000.00"
        )
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,

        @Schema(
                description = "Currency used for the transfer",
                example = "VND"
        )
        @NotNull(message = "Currency is required")
        Currency currency
) {
}
