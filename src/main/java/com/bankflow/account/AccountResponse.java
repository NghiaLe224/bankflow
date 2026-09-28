package com.bankflow.account;

import java.math.BigDecimal;

public class AccountResponse {
    private Long id;
    private String owner;
    private BigDecimal balance;
    private String status;

    public AccountResponse(Long id, String owner, BigDecimal balance, String status) {
        this.id = id;
        this.owner = owner;
        this.balance = balance;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getStatus() {
        return status;
    }
}
