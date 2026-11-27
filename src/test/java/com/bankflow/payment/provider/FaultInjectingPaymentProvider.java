package com.bankflow.payment.provider;

import java.util.Objects;

public class FaultInjectingPaymentProvider implements PaymentProvider {

    private final PaymentProvider delegate;
    private FaultMode faultMode = FaultMode.NONE;

    public FaultInjectingPaymentProvider(PaymentProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public ProviderPaymentResult charge(ProviderPaymentRequest request) {
        return switch (faultMode) {
            case BEFORE_CHARGE_FAILURE -> throw new SimulatedTransientException("Error before charge");
            case AFTER_CHARGE_TIMEOUT -> {
                delegate.charge(request);
                throw new SimulatedTimeoutException("Error after provider charged");
            }
            case NONE -> delegate.charge(request);
        };
    }

    @Override
    public ProviderPaymentResult getStatus(String merchantReference) {
        return delegate.getStatus(merchantReference);
    }

    public void setFaultMode(FaultMode faultMode) {
        this.faultMode = Objects.requireNonNull(faultMode);
    }
}
