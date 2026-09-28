package com.bankflow.account;

import java.math.BigDecimal;

public class CreateAccountRequest {
    private String owner;
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
