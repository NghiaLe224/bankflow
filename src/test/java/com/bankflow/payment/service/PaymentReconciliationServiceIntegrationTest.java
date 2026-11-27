package com.bankflow.payment.service;

import com.bankflow.common.exception.TransientProviderException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class PaymentReconciliationServiceIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentCreationService paymentCreationService;

    @Autowired
    private PaymentResultService paymentResultService;

    @Autowired
    private PaymentReconciliationService reconciliationService;

    @MockitoBean
    private PaymentProvider provider;

    private Long paymentId;

    private final BigDecimal amount =
            new BigDecimal("100000.00");

    @AfterEach
    void cleanUp() {
        if (paymentId != null) {
            paymentRepository.deleteById(paymentId);
        }
    }

    private Long createUnknownPayment(String merchantReference) {
        paymentId = paymentCreationService.createPending(
                merchantReference,
                amount,
                Currency.VND
        );

        paymentResultService.markUnknown(paymentId);

        return paymentId;
    }

    @Test
    void reconciliationService_shouldBeRegisteredAsSpringBean() {
        assertNotNull(reconciliationService);
    }

    @Test
    void unknownPayment_providerSuccess_shouldPersistSuccess() {
        String merchantRef = "PAY-" + UUID.randomUUID();

        createUnknownPayment(merchantRef);

        PaymentEntity before = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.UNKNOWN, before.getStatus());
        assertEquals(1L, before.getVersion());

        ProviderPaymentResult success = new ProviderPaymentResult(
                "PROVIDER-" + UUID.randomUUID(),
                ProviderPaymentStatus.SUCCESS
        );

        when(provider.getStatus(merchantRef))
                .thenReturn(success);

        reconciliationService.reconcile(paymentId);

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, after.getStatus());

        assertEquals(
                success.providerReference(),
                after.getProviderReference()
        );

        assertEquals(2L, after.getVersion());

        verify(provider, never()).charge(any());
        verify(provider, times(1)).getStatus(merchantRef);
    }

    @Test
    void unknownPayment_providerFailed_shouldPersistFailure() {
        String merchantRef = "PAY-" + UUID.randomUUID();

        createUnknownPayment(merchantRef);

        ProviderPaymentResult failed = new ProviderPaymentResult(
                "PROVIDER-" + UUID.randomUUID(),
                ProviderPaymentStatus.FAILED
        );

        when(provider.getStatus(merchantRef))
                .thenReturn(failed);

        reconciliationService.reconcile(paymentId);

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.FAILED, after.getStatus());

        assertEquals(
                failed.providerReference(),
                after.getProviderReference()
        );

        assertEquals(2L, after.getVersion());

        verify(provider, never()).charge(any());
        verify(provider, times(1)).getStatus(merchantRef);
    }

    @Test
    void unknownPayment_providerPending_shouldRemainUnknown() {
        String merchantRef = "PAY-" + UUID.randomUUID();

        createUnknownPayment(merchantRef);

        ProviderPaymentResult pending = new ProviderPaymentResult(
                "PROVIDER-" + UUID.randomUUID(),
                ProviderPaymentStatus.PENDING
        );

        when(provider.getStatus(merchantRef))
                .thenReturn(pending);

        reconciliationService.reconcile(paymentId);

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.UNKNOWN, after.getStatus());
        assertEquals(1L, after.getVersion());
        assertNull(after.getProviderReference());

        verify(provider, never()).charge(any());
        verify(provider, times(1)).getStatus(merchantRef);
    }

    @Test
    void providerLookupFailure_shouldKeepUnknownInDatabase() {
        String merchantRef = "PAY-" + UUID.randomUUID();

        createUnknownPayment(merchantRef);

        when(provider.getStatus(merchantRef))
                .thenThrow(new RuntimeException("Temporary outage"));

        assertThrows(
                RuntimeException.class,
                () -> reconciliationService.reconcile(paymentId)
        );

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.UNKNOWN, after.getStatus());
        assertEquals(1L, after.getVersion());
        assertNull(after.getProviderReference());

        verify(provider, never()).charge(any());
        verify(provider, times(1)).getStatus(merchantRef);
    }


    @Test
    void transientFailures_thenSuccess_shouldPersistSuccessOnlyOnce() {
        String merchantRef = "PAY-" + UUID.randomUUID();
        createUnknownPayment(merchantRef);

        ProviderPaymentResult success = new ProviderPaymentResult(
                "PROVIDER-" + UUID.randomUUID(),
                ProviderPaymentStatus.SUCCESS
        );

        when(provider.getStatus(merchantRef))
                .thenThrow(new TransientProviderException("503 - first"))
                .thenThrow(new TransientProviderException("503 - second"))
                .thenReturn(success);

        reconciliationService.reconcile(paymentId);

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.SUCCESS, after.getStatus());
        assertEquals(success.providerReference(),
                after.getProviderReference());

        assertEquals(2L, after.getVersion());

        verify(provider, times(3)).getStatus(merchantRef);
        verify(provider, never()).charge(any());
    }

    @Test
    void exhaustedRetries_shouldKeepUnknownInDatabase() {
        String merchantRef = "PAY-" + UUID.randomUUID();
        createUnknownPayment(merchantRef);

        when(provider.getStatus(merchantRef))
                .thenThrow(new TransientProviderException("503"));

        assertThrows(
                TransientProviderException.class,
                () -> reconciliationService.reconcile(paymentId)
        );

        PaymentEntity after = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.UNKNOWN, after.getStatus());
        assertEquals(1L, after.getVersion());
        assertNull(after.getProviderReference());

        verify(provider, times(3)).getStatus(merchantRef);
        verify(provider, never()).charge(any());
    }

}