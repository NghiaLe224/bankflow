
package com.bankflow.payment.provider;

import com.bankflow.payment.provider.fake.FakePaymentProvider;
import com.bankflow.transfer.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaultInjectingPaymentProviderTest {

    @Test
    void charge_shouldDelegateToOriginalProvider() {
        // Arrange
        PaymentProvider delegate = mock(PaymentProvider.class);

        FaultInjectingPaymentProvider wrapper =
                new FaultInjectingPaymentProvider(delegate);

        ProviderPaymentRequest request = new ProviderPaymentRequest(
                "PAY-001",
                new BigDecimal("500000"),
                Currency.VND
        );

        ProviderPaymentResult expectedResult =
                mock(ProviderPaymentResult.class);

        when(delegate.charge(request)).thenReturn(expectedResult);

        // Act
        ProviderPaymentResult actualResult = wrapper.charge(request);

        // Assert
        assertSame(expectedResult, actualResult);
        verify(delegate, times(1)).charge(request);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void getStatus_shouldDelegateToOriginalProvider() {
        // Arrange
        PaymentProvider delegate = mock(PaymentProvider.class);

        FaultInjectingPaymentProvider wrapper =
                new FaultInjectingPaymentProvider(delegate);

        String merchantReference = "PAY-001";

        ProviderPaymentResult expectedResult =
                mock(ProviderPaymentResult.class);

        when(delegate.getStatus(merchantReference))
                .thenReturn(expectedResult);

        // Act
        ProviderPaymentResult actualResult =
                wrapper.getStatus(merchantReference);

        // Assert
        assertSame(expectedResult, actualResult);
        verify(delegate, times(1)).getStatus(merchantReference);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void constructor_nullDelegate_shouldReject() {
        // Act & Assert
        assertThrows(
                NullPointerException.class,
                () -> new FaultInjectingPaymentProvider(null)
        );
    }

    @Test
    void beforeChargeFailure_shouldThrowSimulatedTransientException() {
        PaymentProvider delegate = mock(PaymentProvider.class);

        FaultInjectingPaymentProvider wrapper = new FaultInjectingPaymentProvider(delegate);
        wrapper.setFaultMode(FaultMode.BEFORE_CHARGE_FAILURE);

        ProviderPaymentRequest request = new ProviderPaymentRequest(
                "PAY-001",
                new BigDecimal("500000"),
                Currency.VND
        );

        assertThrows(SimulatedTransientException.class, () -> wrapper.charge(request));
        verify(delegate, never()).charge(any());
    }

    @Test
    void afterChargeFailure_shouldThrowSimulatedTimeoutException() {
        PaymentProvider delegate = mock(PaymentProvider.class);
        FaultInjectingPaymentProvider wrapper = new FaultInjectingPaymentProvider(delegate);
        wrapper.setFaultMode(FaultMode.AFTER_CHARGE_TIMEOUT);
        ProviderPaymentRequest request = new ProviderPaymentRequest(
                "PAY-001",
                new BigDecimal("500000"),
                Currency.VND
        );
        ProviderPaymentResult expectedResult = mock(ProviderPaymentResult.class);
        when(delegate.charge(request)).thenReturn(expectedResult);
        assertThrows(SimulatedTimeoutException.class, () -> wrapper.charge(request));
        verify(delegate, times(1)).charge(request);
    }

    @Test
    void afterChargeTimeout_providerShouldAlreadyBeSuccessful() {
        FakePaymentProvider fakeProvider = new FakePaymentProvider();
        FaultInjectingPaymentProvider wrapper =
                new FaultInjectingPaymentProvider(fakeProvider);
        wrapper.setFaultMode(FaultMode.AFTER_CHARGE_TIMEOUT);
        ProviderPaymentRequest request = new ProviderPaymentRequest(
                "PAY-001",
                new BigDecimal("500000"),
                Currency.VND
        );

        assertThrows(
                SimulatedTimeoutException.class,
                () -> wrapper.charge(request)
        );

        ProviderPaymentResult status =
                wrapper.getStatus("PAY-001");

        assertNotNull(status);
        assertEquals(
                ProviderPaymentStatus.SUCCESS,
                status.status()
        );

        ProviderPaymentResult replay = fakeProvider.charge(request);

        assertEquals(
                status.providerReference(),
                replay.providerReference()
        );
    }
}
