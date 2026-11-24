package com.bankflow.payment.entity;

import com.bankflow.payment.domain.PaymentStateMachine;
import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.transfer.enums.Currency;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version()
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(
            name = "merchant_reference",
            nullable = false,
            unique = true,
            length = 100,
            updatable = false
    )
    private String merchantReference;

    protected PaymentEntity() {
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public static PaymentEntity createPending(
            BigDecimal amount,
            Currency currency,
            String merchantReference
    ) {
        if(amount == null) {
            throw new IllegalArgumentException("Payment: amount cannot be null");
        }

        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment: amount must be greater than 0");
        }

        if(currency == null) {
            throw new IllegalArgumentException("Payment: currency cannot be null");
        }

        if(amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Payment: amount must have at most 2 decimal places");
        }

        if(merchantReference == null || merchantReference.isBlank()) {
            throw new IllegalArgumentException("Payment: merchant reference cannot be null or blank");
        }

        if(merchantReference.length() > 100) {
            throw new IllegalArgumentException("Payment: merchant reference exceeds maximum length");
        }

        PaymentEntity payment = new PaymentEntity();

        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.merchantReference = merchantReference;

        return payment;
    }


    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public String getMerchantReference() {
        return merchantReference;
    }

    public void transitionTo(PaymentStatus next) {
        if (next == null) {
            throw new IllegalArgumentException(
                    "Payment status cannot be null"
            );
        }

        if (!PaymentStateMachine.canTransition(this.status, next)) {
            throw new IllegalStateException(
                    "Cannot transition payment from "
                            + this.status + " to " + next
            );
        }

        if (this.status == next) {
            return;
        }

        this.status = next;
    }

    public void applyProviderResult(ProviderPaymentResult result) {
        if(result == null) {
            throw new IllegalArgumentException("Provider result is null");
        }

        String incomingRef = result.providerReference();

        if(incomingRef == null || incomingRef.isBlank()) {
            throw new IllegalArgumentException("Provider reference is required");
        }

        if(incomingRef.length() > 100) {
            throw new IllegalArgumentException("Provider reference is too long");
        }

        if(result.status() == null) {
            throw new IllegalArgumentException("Provider status cannot be null");
        }

        if(this.providerReference != null && !this.providerReference.equals(incomingRef)) {
            throw new IllegalStateException(
                    "Conflicting provider reference"
            );
        }

        PaymentStatus next = switch (result.status()) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case FAILED -> PaymentStatus.FAILED;
            case PENDING -> PaymentStatus.PENDING;
        };

        transitionTo(next);

        if(this.providerReference == null) {
            this.providerReference = incomingRef;
        }
    }


}
