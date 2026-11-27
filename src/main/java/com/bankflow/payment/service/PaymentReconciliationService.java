package com.bankflow.payment.service;

import com.bankflow.common.exception.PaymentNotFoundException;
import com.bankflow.common.exception.TransientProviderException;
import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.entity.PaymentEntity;
import com.bankflow.payment.provider.PaymentProvider;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.payment.repository.PaymentRepository;
import com.bankflow.payment.resilience.RetryExecutor;
import org.springframework.stereotype.Service;

@Service
public class PaymentReconciliationService {
    private final PaymentProvider paymentProvider;
    private final PaymentRepository paymentRepository;
    private final PaymentResultService paymentResultService;
    private final RetryExecutor retryExecutor;

    public PaymentReconciliationService(
            PaymentProvider paymentProvider, PaymentRepository paymentRepository, PaymentResultService paymentResultService,
            RetryExecutor retryExecutor
    ) {
        this.paymentProvider = paymentProvider;
        this.paymentRepository = paymentRepository;
        this.paymentResultService = paymentResultService;
        this.retryExecutor = retryExecutor;
    }

    public void reconcile(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment with id: " + paymentId + " not found"));

        if(payment.getStatus() != PaymentStatus.UNKNOWN) {
            return;
        }
        ProviderPaymentResult providerPaymentResult = retryExecutor.execute(
                () -> paymentProvider.getStatus(payment.getMerchantReference()),
                exception -> exception instanceof TransientProviderException
        );
        if (providerPaymentResult == null || providerPaymentResult.status() == null) {
            throw new IllegalStateException(
                    "Provider returned an invalid status response"
            );
        }
        if(providerPaymentResult.status() == ProviderPaymentStatus.SUCCESS || providerPaymentResult.status() == ProviderPaymentStatus.FAILED) {
            paymentResultService.applyResult(paymentId, providerPaymentResult);
        }

    }
}
