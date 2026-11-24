package com.bankflow.payment.provider;

import com.bankflow.transfer.enums.Currency;

import java.math.BigDecimal;

public record ProviderPaymentRequest(
        String merchantReference,
        BigDecimal amount,
        Currency currency
) {
}
