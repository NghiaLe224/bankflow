
package com.bankflow.payment.service;

import com.bankflow.common.exception.PaymentOutcomeUnknownException;
import com.bankflow.payment.provider.PaymentProvider;
import com.bankflow.payment.provider.ProviderPaymentRequest;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.transfer.enums.Currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentCreationService creationService;

    @Mock
    private PaymentResultService resultService;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private PaymentService paymentService;

    private String merchantReference;
    private BigDecimal amount;
    private Currency currency;
    private Long paymentId;
    private ProviderPaymentRequest request;
    private ProviderPaymentResult providerResult;

    @BeforeEach
    void setUp() {
        merchantReference = "PAY-101";
        amount = new BigDecimal("100000.00");
        currency = Currency.VND;
        paymentId = 101L;

        request = new ProviderPaymentRequest(
                merchantReference,
                amount,
                currency
        );

        providerResult = new ProviderPaymentResult(
                "FAKE-001",
                ProviderPaymentStatus.SUCCESS
        );
    }

    // TEST 1: Happy path
    @Test
    void processPayment_success_shouldCallServicesInCorrectOrder() {
        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenReturn(paymentId);

        when(paymentProvider.charge(request))
                .thenReturn(providerResult);

        Long actualId = paymentService.processPayment(
                merchantReference,
                amount,
                currency
        );

        assertEquals(paymentId, actualId);

        InOrder inOrder = inOrder(
                creationService,
                paymentProvider,
                resultService
        );

        inOrder.verify(creationService)
                .createPending(merchantReference, amount, currency);

        inOrder.verify(paymentProvider)
                .charge(request);

        inOrder.verify(resultService)
                .applyResult(paymentId, providerResult);

        inOrder.verifyNoMoreInteractions();
    }

    // TEST 2: Transaction A failure
    @Test
    void processPayment_creationFailure_shouldNotCallProviderOrResultService() {
        RuntimeException failure =
                new RuntimeException("Database insert failed");

        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> paymentService.processPayment(
                        merchantReference,
                        amount,
                        currency
                )
        );

        assertSame(failure, thrown);

        verify(creationService).createPending(
                merchantReference, amount, currency
        );

        verifyNoInteractions(paymentProvider, resultService);
        verifyNoMoreInteractions(creationService);
    }

    // TEST 3: Provider failure
    @Test
    void processPayment_providerFailure_shouldNotApplyResult() {
        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenReturn(paymentId);

        RuntimeException failure =
                new RuntimeException("Provider unavailable");

        when(paymentProvider.charge(request))
                .thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> paymentService.processPayment(
                        merchantReference,
                        amount,
                        currency
                )
        );

        assertSame(failure, thrown);

        InOrder inOrder = inOrder(
                creationService,
                paymentProvider
        );

        inOrder.verify(creationService)
                .createPending(merchantReference, amount, currency);

        inOrder.verify(paymentProvider)
                .charge(request);

        inOrder.verifyNoMoreInteractions();

        verifyNoInteractions(resultService);
    }

    // TEST 4: Transaction B failure
    @Test
    void processPayment_resultFailure_shouldNotRetryCharge() {
        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenReturn(paymentId);

        when(paymentProvider.charge(request))
                .thenReturn(providerResult);

        RuntimeException failure =
                new RuntimeException("Database update failed");

        doThrow(failure).when(resultService)
                .applyResult(paymentId, providerResult);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> paymentService.processPayment(
                        merchantReference,
                        amount,
                        currency
                )
        );

        assertSame(failure, thrown);

        InOrder inOrder = inOrder(
                creationService,
                paymentProvider,
                resultService
        );

        inOrder.verify(creationService)
                .createPending(merchantReference, amount, currency);

        inOrder.verify(paymentProvider)
                .charge(request);

        inOrder.verify(resultService)
                .applyResult(paymentId, providerResult);

        inOrder.verifyNoMoreInteractions();

        verify(paymentProvider, times(1)).charge(request);
    }

    // TEST 5: Provider returns FAILED
    @Test
    void processPayment_failedProviderResult_shouldStillPersistResult() {
        ProviderPaymentResult failedResult =
                new ProviderPaymentResult(
                        "FAKE-002",
                        ProviderPaymentStatus.FAILED
                );

        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenReturn(paymentId);

        when(paymentProvider.charge(request))
                .thenReturn(failedResult);

        Long actualId = paymentService.processPayment(
                merchantReference,
                amount,
                currency
        );

        assertEquals(paymentId, actualId);

        verify(resultService).applyResult(
                paymentId,
                failedResult
        );

        verify(paymentProvider, times(1)).charge(request);
    }

    // TEST 6: Provider returns PENDING
    @Test
    void processPayment_pendingProviderResult_shouldStillPersistResult() {
        ProviderPaymentResult pendingResult =
                new ProviderPaymentResult(
                        "FAKE-003",
                        ProviderPaymentStatus.PENDING
                );

        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenReturn(paymentId);

        when(paymentProvider.charge(request))
                .thenReturn(pendingResult);

        Long actualId = paymentService.processPayment(
                merchantReference,
                amount,
                currency
        );

        assertEquals(paymentId, actualId);

        verify(resultService).applyResult(
                paymentId,
                pendingResult
        );
    }


    @Test
    void processPayment_unknownOutcome_shouldMarkUnknownWithoutRetry() {
        // Arrange
        when(creationService.createPending(
                merchantReference, amount, currency
        )).thenReturn(paymentId);

        PaymentOutcomeUnknownException timeout =
                new PaymentOutcomeUnknownException(
                        "Provider response timeout"
                );

        when(paymentProvider.charge(request))
                .thenThrow(timeout);

        // Act
        Long actualId = paymentService.processPayment(
                merchantReference,
                amount,
                currency
        );

        // Assert
        assertEquals(paymentId, actualId);

        InOrder inOrder = inOrder(
                creationService,
                paymentProvider,
                resultService
        );

        inOrder.verify(creationService)
                .createPending(merchantReference, amount, currency);

        inOrder.verify(paymentProvider)
                .charge(request);

        inOrder.verify(resultService)
                .markUnknown(paymentId);

        inOrder.verifyNoMoreInteractions();

        verify(paymentProvider, times(1)).charge(request);
        verify(resultService, never()).applyResult(any(), any());
    }

}
