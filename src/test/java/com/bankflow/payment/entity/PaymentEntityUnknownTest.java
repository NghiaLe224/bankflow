
package com.bankflow.payment.entity;

import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.transfer.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEntityUnknownTest {

    private PaymentEntity newPendingPayment() {
        return PaymentEntity.createPending(
                new BigDecimal("500000"),
                Currency.VND,
                "PAY-001"
        );
    }

    private ProviderPaymentResult result(
            ProviderPaymentStatus status
    ) {
        return new ProviderPaymentResult(
                "PROVIDER-001",
                status
        );
    }

    @Test
    void pendingToUnknown_shouldSucceed() {
        PaymentEntity payment = newPendingPayment();

        payment.markUnknown();

        assertEquals(PaymentStatus.UNKNOWN, payment.getStatus());
        assertNull(payment.getProviderReference());
    }

    @Test
    void unknownToSuccess_shouldSucceed() {
        PaymentEntity payment = newPendingPayment();
        payment.markUnknown();

        payment.applyProviderResult(
                result(ProviderPaymentStatus.SUCCESS)
        );

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("PROVIDER-001", payment.getProviderReference());
    }

    @Test
    void unknownToFailed_shouldSucceed() {
        PaymentEntity payment = newPendingPayment();
        payment.markUnknown();

        payment.applyProviderResult(
                result(ProviderPaymentStatus.FAILED)
        );

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void successToUnknown_shouldReject() {
        PaymentEntity payment = newPendingPayment();
        payment.applyProviderResult(
                result(ProviderPaymentStatus.SUCCESS)
        );

        assertThrows(
                IllegalStateException.class,
                payment::markUnknown
        );

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void failedToUnknown_shouldReject() {
        PaymentEntity payment = newPendingPayment();
        payment.applyProviderResult(
                result(ProviderPaymentStatus.FAILED)
        );

        assertThrows(
                IllegalStateException.class,
                payment::markUnknown
        );

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void unknownToUnknown_shouldBeNoOp() {
        PaymentEntity payment = newPendingPayment();
        payment.markUnknown();

        assertDoesNotThrow(payment::markUnknown);

        assertEquals(PaymentStatus.UNKNOWN, payment.getStatus());
    }
}
