
package com.bankflow.payment.service;

import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.entity.PaymentEntity;
import com.bankflow.payment.provider.PaymentProvider;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.payment.repository.PaymentRepository;
import com.bankflow.transfer.enums.Currency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentProvider paymentProvider;

    private Long createdPaymentId;

    @AfterEach
    void cleanup() {
        if (createdPaymentId != null) {
            paymentRepository.deleteById(createdPaymentId);
        }
    }

    @Test
    void processPayment_shouldCompleteFullPaymentFlow() {
        String merchantReference = "PAY-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100000.00");

        createdPaymentId = paymentService.processPayment(
                merchantReference,
                amount,
                Currency.VND
        );

        PaymentEntity payment = paymentRepository
                .findById(createdPaymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(merchantReference, payment.getMerchantReference());
        assertEquals(0, amount.compareTo(payment.getAmount()));
        assertEquals(Currency.VND, payment.getCurrency());

        assertNotNull(payment.getProviderReference());
        assertFalse(payment.getProviderReference().isBlank());
        assertEquals(1L, payment.getVersion());

        ProviderPaymentResult providerResult =
                paymentProvider.getStatus(merchantReference);

        assertEquals(
                ProviderPaymentStatus.SUCCESS,
                providerResult.status()
        );

        assertEquals(
                providerResult.providerReference(),
                payment.getProviderReference()
        );
    }
}
