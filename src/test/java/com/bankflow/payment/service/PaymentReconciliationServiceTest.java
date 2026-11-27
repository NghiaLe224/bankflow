
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PaymentResultService paymentResultService;

    @Spy
    private RetryExecutor retryExecutor = new RetryExecutor(
            3,
            retryNumber -> 0L,
            millis -> {}
    );

    @InjectMocks
    private PaymentReconciliationService reconciliationService;

    private static final Long PAYMENT_ID = 101L;
    private static final String MERCHANT_REF = "PAY-101";
    private static final String PROVIDER_REF = "PROVIDER-001";

    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        payment = mock(PaymentEntity.class);
    }

    private void givenPaymentWithStatus(PaymentStatus status) {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn(status);
    }

    private void givenUnknownPayment() {
        givenPaymentWithStatus(PaymentStatus.UNKNOWN);

        when(payment.getMerchantReference())
                .thenReturn(MERCHANT_REF);
    }

    private ProviderPaymentResult result(
            ProviderPaymentStatus status
    ) {
        return new ProviderPaymentResult(PROVIDER_REF, status);
    }

    @Test
    void paymentNotFound_shouldThrowException() {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        verifyNoInteractions(paymentProvider, paymentResultService);
    }

    @Test
    void successPayment_shouldSkipReconciliation() {
        givenPaymentWithStatus(PaymentStatus.SUCCESS);

        reconciliationService.reconcile(PAYMENT_ID);

        verifyNoInteractions(paymentProvider, paymentResultService);
    }

    @Test
    void failedPayment_shouldSkipReconciliation() {
        givenPaymentWithStatus(PaymentStatus.FAILED);

        reconciliationService.reconcile(PAYMENT_ID);

        verifyNoInteractions(paymentProvider, paymentResultService);
    }

    @Test
    void pendingPayment_shouldSkipReconciliation() {
        givenPaymentWithStatus(PaymentStatus.PENDING);

        reconciliationService.reconcile(PAYMENT_ID);

        verifyNoInteractions(paymentProvider, paymentResultService);
    }

    @Test
    void unknownPayment_providerSuccess_shouldApplyResult() {
        givenUnknownPayment();

        ProviderPaymentResult result =
                result(ProviderPaymentStatus.SUCCESS);

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenReturn(result);

        reconciliationService.reconcile(PAYMENT_ID);

        verify(paymentProvider).getStatus(MERCHANT_REF);
        verify(paymentResultService).applyResult(PAYMENT_ID, result);
        verify(paymentProvider, never()).charge(any());
        verifyNoMoreInteractions(paymentProvider, paymentResultService);
    }

    @Test
    void unknownPayment_providerFailed_shouldApplyResult() {
        givenUnknownPayment();

        ProviderPaymentResult result =
                result(ProviderPaymentStatus.FAILED);

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenReturn(result);

        reconciliationService.reconcile(PAYMENT_ID);

        verify(paymentProvider).getStatus(MERCHANT_REF);
        verify(paymentResultService).applyResult(PAYMENT_ID, result);
        verify(paymentProvider, never()).charge(any());
        verifyNoMoreInteractions(paymentProvider, paymentResultService);
    }

    @Test
    void unknownPayment_providerPending_shouldNotUpdate() {
        givenUnknownPayment();

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenReturn(result(ProviderPaymentStatus.PENDING));

        reconciliationService.reconcile(PAYMENT_ID);

        verify(paymentProvider).getStatus(MERCHANT_REF);
        verifyNoInteractions(paymentResultService);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void providerReturnsNull_shouldThrowWithoutUpdating() {
        givenUnknownPayment();

        // Mockito returns null by default for an unstubbed method.

        assertThrows(
                IllegalStateException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        verify(paymentProvider).getStatus(MERCHANT_REF);
        verifyNoInteractions(paymentResultService);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void providerReturnsNullStatus_shouldThrowWithoutUpdating() {
        givenUnknownPayment();

        ProviderPaymentResult invalidResult =
                mock(ProviderPaymentResult.class);

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenReturn(invalidResult);

        assertThrows(
                IllegalStateException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        verifyNoInteractions(paymentResultService);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void providerLookupFailure_shouldPropagateWithoutUpdating() {
        givenUnknownPayment();

        RuntimeException failure =
                new RuntimeException("Provider unavailable");

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        assertSame(failure, thrown);

        verifyNoInteractions(paymentResultService);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void transientFailureTwice_thenSuccess_shouldRetryThreeTimes() {
        givenUnknownPayment();

        ProviderPaymentResult success =
                result(ProviderPaymentStatus.SUCCESS);

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenThrow(new TransientProviderException("503 - first failure"))
                .thenThrow(new TransientProviderException("503 - second failure"))
                .thenReturn(success);

        reconciliationService.reconcile(PAYMENT_ID);

        verify(paymentProvider, times(3)).getStatus(MERCHANT_REF);
        verify(paymentResultService, times(1))
                .applyResult(PAYMENT_ID, success);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void transientFailures_exhausted_shouldStopWithoutApplyingResult() {
        givenUnknownPayment();

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenThrow(new TransientProviderException("503"));

        assertThrows(
                TransientProviderException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        verify(paymentProvider, times(3))
                .getStatus(MERCHANT_REF);

        verifyNoInteractions(paymentResultService);

        verify(paymentProvider, never()).charge(any());
    }


    @Test
    void nonRetryableFailure_shouldStopAfterFirstAttempt() {
        givenUnknownPayment();

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenThrow(new IllegalArgumentException("Invalid reference"));

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        assertEquals("Invalid reference", thrown.getMessage());

        verify(paymentProvider, times(1))
                .getStatus(MERCHANT_REF);

        verifyNoInteractions(paymentResultService);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void transientFailure_thenNonRetryable_shouldStopAtSecondAttempt() {
        givenUnknownPayment();

        when(paymentProvider.getStatus(MERCHANT_REF))
                .thenThrow(new TransientProviderException("503"))
                .thenThrow(new IllegalArgumentException("Invalid reference"));

        assertThrows(
                IllegalArgumentException.class,
                () -> reconciliationService.reconcile(PAYMENT_ID)
        );

        verify(paymentProvider, times(2))
                .getStatus(MERCHANT_REF);

        verifyNoInteractions(paymentResultService);
        verify(paymentProvider, never()).charge(any());
    }

}
