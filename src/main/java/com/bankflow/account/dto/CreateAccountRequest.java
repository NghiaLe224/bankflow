package com.bankflow.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateAccountRequest {
    @NotBlank(message = "Owner must not be blank")
    @Size(min = 2, max = 100, message = "Owner must be between 2 and 100 characters")
    private String owner;

    @NotNull(message = "Initial balance is required")
    @PositiveOrZero(message = "Initial balance must be greater than or equal to 0")
    private BigDecimal initialBalance;

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }
}
