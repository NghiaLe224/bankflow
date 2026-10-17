package com.bankflow.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateAccountRequest {

    @NotBlank(message = "Account number must not be blank")
    @Size(max = 30, message = "Account number must not exceed 30 characters")
    private String accountNumber;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}
