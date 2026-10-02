package com.bankflow.ledger.entity;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.ledger.enums.LedgerEntryType;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private TransferEntity transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    protected LedgerEntryEntity() {
    }

    public LedgerEntryEntity(TransferEntity transfer, AccountEntity account, LedgerEntryType entryType, BigDecimal amount, Currency currency) {
        this.transfer = transfer;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public TransferEntity getTransfer() {
        return transfer;
    }

    public AccountEntity getAccount() {
        return account;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
