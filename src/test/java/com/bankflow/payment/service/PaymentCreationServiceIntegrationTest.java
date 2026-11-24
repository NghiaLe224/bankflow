package com.bankflow.payment.service;

import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.entity.PaymentEntity;
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
class PaymentCreationServiceIntegrationTest {

    @Autowired
    private PaymentCreationService creationService;

    @Autowired
    private PaymentRepository paymentRepository;

    private Long createdPaymentId;

    @AfterEach
    void cleanup() {
        if (createdPaymentId != null) {
            paymentRepository.deleteById(createdPaymentId);
        }
    }

    @Test
    void shouldCommitPendingPayment() {
        String merchantReference = "PAY-" + UUID.randomUUID();

        Long paymentId = creationService.createPending(
                merchantReference,
                new BigDecimal("100000.00"),
                Currency.VND
        );

        createdPaymentId = paymentId;

        PaymentEntity reloaded = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertNotNull(paymentId);
        assertEquals(PaymentStatus.PENDING, reloaded.getStatus());
        assertEquals(merchantReference, reloaded.getMerchantReference());

        assertEquals(
                0,
                new BigDecimal("100000.00").compareTo(reloaded.getAmount())
        );

        assertEquals(Currency.VND, reloaded.getCurrency());
        assertNull(reloaded.getProviderReference());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getVersion());
    }
}
