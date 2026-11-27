package com.bankflow.payment.service;

import com.bankflow.payment.entity.PaymentEntity;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bankflow.common.exception.PaymentNotFoundException;

@Service
public class PaymentResultService {
    private final PaymentRepository paymentRepository;

    public PaymentResultService(
            PaymentRepository paymentRepository
    ) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void applyResult(
            Long paymentId,
            ProviderPaymentResult result
    ) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment with id: " + paymentId + " not found"));

        payment.applyProviderResult(result);
    }

    @Transactional
    public void markUnknown(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment with id: " + paymentId + " not found"));
        payment.markUnknown();
    }
}
