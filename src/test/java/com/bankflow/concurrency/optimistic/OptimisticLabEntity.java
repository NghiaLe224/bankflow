package com.bankflow.concurrency.optimistic;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bankflow_optimistic_lab")
public class OptimisticLabEntity {

    @Id
    private Long id;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal balance;

    @Version
    private Long version;

    protected OptimisticLabEntity() {
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
