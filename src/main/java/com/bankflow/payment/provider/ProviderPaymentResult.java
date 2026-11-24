package com.bankflow.payment.provider;

public record ProviderPaymentResult(
        String providerReference,
        ProviderPaymentStatus status
) {
}
