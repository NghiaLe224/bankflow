package com.bankflow.account.entity;

import com.bankflow.account.enums.AccountStatus;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.user.entity.UserEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(
            name = "account_number",
            nullable = false,
            unique = true,
            length = 30
    )
    private String accountNumber;

    @Column(
            name = "balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private AccountStatus status;

    protected AccountEntity() {
    }

    public AccountEntity(UserEntity user, String accountNumber, BigDecimal balance, AccountStatus status) {
        this.user = user;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void block() {
        this.status = AccountStatus.BLOCKED;
    }

    public void assignUser(UserEntity user) {
        this.user = user;
    }

    public void debit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Debit amount must be greater than zero"
            );
        }

        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id);
        }

        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Credit amount must be greater than zero"
            );
        }

        this.balance = this.balance.add(amount);
    }
}
