package com.bankflow.payment.service;

import com.bankflow.payment.entity.PaymentEntity;
import com.bankflow.payment.repository.PaymentRepository;
import com.bankflow.transfer.enums.Currency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentCreationService {

    private final PaymentRepository paymentRepository;

    public PaymentCreationService(
            PaymentRepository paymentRepository
    ) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Long createPending(String merchantReference, BigDecimal amount, Currency currency) {
        PaymentEntity savedPayment = paymentRepository.save(PaymentEntity.createPending(amount, currency, merchantReference));
        return savedPayment.getId();
    }
}
