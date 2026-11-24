package com.bankflow.payment.provider;

public interface PaymentProvider {
    ProviderPaymentResult charge(ProviderPaymentRequest request);

    ProviderPaymentResult getStatus(String merchantReference);
}
