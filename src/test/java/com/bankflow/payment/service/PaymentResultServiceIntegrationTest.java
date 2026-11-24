package com.bankflow.payment.service;

import com.bankflow.common.exception.PaymentNotFoundException;
import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.entity.PaymentEntity;
import com.bankflow.payment.provider.ProviderPaymentRequest;
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
public class PaymentResultServiceIntegrationTest {

    private Long paymentId;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentCreationService paymentCreationService;
    @Autowired
    private PaymentResultService paymentResultService;

    @AfterEach
    void cleanUp() {
        if(paymentId != null) {
            paymentRepository.deleteById(paymentId);
        }
    }

    @Test
    void shouldPersistSuccessfulProviderResult() {
        paymentId = paymentCreationService.createPending(
                "PAY-" + UUID.randomUUID(),
                new BigDecimal("100000.00"),
                Currency.VND
        );

        PaymentEntity before = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.PENDING, before.getStatus());
        assertEquals(0L, before.getVersion());

        ProviderPaymentResult result = new ProviderPaymentResult(
                "FAKE-" + UUID.randomUUID(),
                ProviderPaymentStatus.SUCCESS
        );

        paymentResultService.applyResult(paymentId, result);

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, after.getStatus());
        assertEquals(result.providerReference(), after.getProviderReference());
        assertEquals(1L, after.getVersion());
    }


    @Test
    void shouldPersistFailedProviderResult() {
        paymentId = paymentCreationService.createPending(
                "PAY-" + UUID.randomUUID(),
                new BigDecimal("100000.00"),
                Currency.VND
        );

        ProviderPaymentResult result = new ProviderPaymentResult(
                "FAKE-" + UUID.randomUUID(),
                ProviderPaymentStatus.FAILED
        );

        paymentResultService.applyResult(paymentId, result);

        PaymentEntity reloaded = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.FAILED, reloaded.getStatus());
        assertEquals(result.providerReference(), reloaded.getProviderReference());
        assertEquals(1L, reloaded.getVersion());
    }

    @Test
    void invalidTransition_shouldRollbackAndPreserveDatabaseState() {
        paymentId = paymentCreationService.createPending(
                "PAY-" + UUID.randomUUID(),
                new BigDecimal("100000.00"),
                Currency.VND
        );

        String providerRef = "FAKE-" + UUID.randomUUID();

        paymentResultService.applyResult(
                paymentId,
                new ProviderPaymentResult(
                        providerRef,
                        ProviderPaymentStatus.SUCCESS
                )
        );

        PaymentEntity before = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, before.getStatus());
        assertEquals(1L, before.getVersion());

        assertThrows(IllegalStateException.class, () ->
                paymentResultService.applyResult(
                        paymentId,
                        new ProviderPaymentResult(
                                providerRef,
                                ProviderPaymentStatus.FAILED
                        )
                )
        );

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, after.getStatus());
        assertEquals(providerRef, after.getProviderReference());
        assertEquals(before.getVersion(), after.getVersion());
    }

    @Test
    void nonexistentPayment_shouldThrowPaymentNotFoundException() {
        Long nonexistentPaymentId = -1L;

        ProviderPaymentResult result = new ProviderPaymentResult(
                "FAKE-" + UUID.randomUUID(),
                ProviderPaymentStatus.SUCCESS
        );

        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> paymentResultService.applyResult(
                        nonexistentPaymentId,
                        result
                )
        );

        assertTrue(exception.getMessage().contains("-1"));
    }


}
