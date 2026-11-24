package com.bankflow.payment.provider.fake;

import com.bankflow.payment.provider.PaymentProvider;
import com.bankflow.payment.provider.ProviderPaymentRequest;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.transfer.enums.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FakePaymentProvider implements PaymentProvider {

    private final ConcurrentHashMap<String, StoredPayment> payments = new ConcurrentHashMap<>();

    private record StoredPayment(
            BigDecimal amount,
            Currency currency,
            ProviderPaymentResult result
    ) {};

    @Override
    public ProviderPaymentResult charge(ProviderPaymentRequest request) {
        if(request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        String merchantReference = request.merchantReference();

        if(merchantReference == null || merchantReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Merchant reference is required"
            );
        }

        if(request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Invalid amount"
            );
        }

        if(request.currency() == null) {
            throw new IllegalArgumentException("Currency is required");
        }

        StoredPayment stored = payments.compute(
                merchantReference,
                (key, existing) -> {
                    if(existing != null) {
                        boolean sameAmount = existing.amount.compareTo(request.amount()) == 0;
                        boolean sameCurrency = existing.currency == request.currency();

                        if(!sameAmount || !sameCurrency) {
                            throw new IllegalArgumentException(
                                    "Merchant reference conflict"
                            );
                        }
                        return existing;
                    }

                    String providerReference = "FAKE-" + UUID.randomUUID();
                    ProviderPaymentResult result = new ProviderPaymentResult(
                            providerReference,
                            ProviderPaymentStatus.SUCCESS
                    );

                    return new StoredPayment(request.amount(), request.currency(), result);
                }
        );
        return stored.result;
    }

    @Override
    public ProviderPaymentResult getStatus(String merchantReference) {
        if(merchantReference == null || merchantReference.isBlank()) {
            throw new IllegalArgumentException("Merchant reference is required");
        }

         StoredPayment stored = payments.get(merchantReference);

         if(stored == null) {
             throw new NoSuchElementException(
                     "Payment not found: " + merchantReference
             );
         }

         return stored.result;
    }
}
