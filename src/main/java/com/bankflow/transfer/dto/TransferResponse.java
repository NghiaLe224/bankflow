package com.bankflow.transfer.dto;

import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransferResponse {

    private Long id;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private Currency currency;
    private TransferStatus status;
    private LocalDateTime createdAt;

    public TransferResponse(Long id, Long fromAccountId, Long toAccountId, BigDecimal amount, Currency currency, TransferStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
