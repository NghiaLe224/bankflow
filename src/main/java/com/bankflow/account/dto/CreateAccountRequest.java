package com.bankflow.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateAccountRequest {

    @NotNull(message = "User id is required")
    private Long userId;

    @NotBlank(message = "Account number must not be blank")
    @Size(max = 30, message = "Account number must not exceed 30 characters")
    private String accountNumber;

    @NotNull(message = "Initial balance is required")
    @PositiveOrZero(message = "Initial balance must be greater than or equal to 0")
    private BigDecimal initialBalance;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }
}
