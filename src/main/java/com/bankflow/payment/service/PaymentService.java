package com.bankflow.payment.service;

import com.bankflow.common.exception.PaymentOutcomeUnknownException;
import com.bankflow.payment.provider.PaymentProvider;
import com.bankflow.payment.provider.ProviderPaymentRequest;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.transfer.enums.Currency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private final PaymentCreationService paymentCreationService;
    private final PaymentResultService paymentResultService;
    private final PaymentProvider paymentProvider;

    public PaymentService (
            PaymentCreationService paymentCreationService,
            PaymentResultService paymentResultService,
            PaymentProvider paymentProvider
    ) {
        this.paymentCreationService = paymentCreationService;
        this.paymentResultService = paymentResultService;
        this.paymentProvider = paymentProvider;
    }

    public Long processPayment(String merchantReference, BigDecimal amount, Currency currency) {
        Long paymentId = paymentCreationService.createPending(merchantReference, amount, currency);
        ProviderPaymentResult providerResult;
        try{
            providerResult = paymentProvider.charge(new ProviderPaymentRequest(
                    merchantReference,
                    amount,
                    currency
            ));
        } catch (PaymentOutcomeUnknownException e) {
            paymentResultService.markUnknown(paymentId);
            return paymentId;
        }
        paymentResultService.applyResult(paymentId, providerResult);

        return paymentId;
    }
}
