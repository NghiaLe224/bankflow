package com.bankflow.payment.entity;

import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.provider.ProviderPaymentResult;
import com.bankflow.payment.provider.ProviderPaymentStatus;
import com.bankflow.transfer.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEntityTest {
    private static final String MERCHANT_REFERENCE = "merchant-ref-001";

    @Test
    void shouldCreatePendingPayment() {
        PaymentEntity payment = PaymentEntity.createPending(
                new BigDecimal("100000.00"),
                Currency.VND,
                MERCHANT_REFERENCE
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(Currency.VND, payment.getCurrency());
        assertEquals(MERCHANT_REFERENCE, payment.getMerchantReference());
        assertNull(payment.getId());
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentEntity.createPending(null, Currency.VND, MERCHANT_REFERENCE)
        );
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentEntity.createPending(
                        BigDecimal.ZERO,
                        Currency.VND,
                        MERCHANT_REFERENCE
                )
        );
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentEntity.createPending(
                        new BigDecimal("-100"),
                        Currency.VND,
                        MERCHANT_REFERENCE
                )
        );
    }

    @Test
    void shouldRejectMoreThanTwoDecimalPlaces() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentEntity.createPending(
                        new BigDecimal("100.001"),
                        Currency.VND,
                        MERCHANT_REFERENCE
                )
        );
    }

    @Test
    void shouldAcceptTrailingZeros() {
        assertDoesNotThrow(
                () -> PaymentEntity.createPending(
                        new BigDecimal("100.000"),
                        Currency.VND,
                        MERCHANT_REFERENCE
                )
        );
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentEntity.createPending(
                        new BigDecimal("100"),
                        null,
                        MERCHANT_REFERENCE
                )
        );
    }

    @Test
    void shouldTransitionPendingToSuccess() {
        PaymentEntity payment = PaymentEntity.createPending(
                new BigDecimal("100000"),
                Currency.VND,
                "PAY-101"
        );

        payment.transitionTo(PaymentStatus.SUCCESS);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void shouldThrowException_whenTransitionFromSuccess() {
        PaymentEntity payment = PaymentEntity.createPending(
                new BigDecimal("100000"),
                Currency.VND,
                "PAY-101"
        );
        payment.transitionTo(PaymentStatus.SUCCESS);
        assertThrows(IllegalStateException.class, () -> payment.transitionTo(PaymentStatus.FAILED));
    }

    @Test
    void shouldThrowException_whenTransitionToNull() {
        PaymentEntity payment = PaymentEntity.createPending(
                new BigDecimal("100000"),
                Currency.VND,
                "PAY-101"
        );
        payment.transitionTo(PaymentStatus.SUCCESS);
        assertThrows(IllegalArgumentException.class, () -> payment.transitionTo(null));
    }


    private PaymentEntity newPendingPayment() {
        return PaymentEntity.createPending(
                new BigDecimal("100000.00"),
                Currency.VND,
                "PAY-101"
        );
    }

    @Test
    void applyProviderResult_success_shouldUpdateStatusAndReference() {
        PaymentEntity payment = newPendingPayment();

        ProviderPaymentResult result = new ProviderPaymentResult(
                "FAKE-001",
                ProviderPaymentStatus.SUCCESS
        );

        payment.applyProviderResult(result);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("FAKE-001", payment.getProviderReference());
    }

    @Test
    void applyProviderResult_failed_shouldUpdateStatusAndReference() {
        PaymentEntity payment = newPendingPayment();

        payment.applyProviderResult(new ProviderPaymentResult(
                "FAKE-002",
                ProviderPaymentStatus.FAILED
        ));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("FAKE-002", payment.getProviderReference());
    }

    @Test
    void applyProviderResult_pending_shouldKeepPendingButSaveReference() {
        PaymentEntity payment = newPendingPayment();

        payment.applyProviderResult(new ProviderPaymentResult(
                "FAKE-003",
                ProviderPaymentStatus.PENDING
        ));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("FAKE-003", payment.getProviderReference());
    }

    @Test
    void applyProviderResult_sameResultTwice_shouldBeIdempotent() {
        PaymentEntity payment = newPendingPayment();

        ProviderPaymentResult result = new ProviderPaymentResult(
                "FAKE-004",
                ProviderPaymentStatus.SUCCESS
        );

        payment.applyProviderResult(result);
        payment.applyProviderResult(result);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("FAKE-004", payment.getProviderReference());
    }

    @Test
    void applyProviderResult_invalidTransition_shouldNotMutateEntity() {
        PaymentEntity payment = newPendingPayment();

        payment.applyProviderResult(new ProviderPaymentResult(
                "FAKE-005",
                ProviderPaymentStatus.SUCCESS
        ));

        assertThrows(IllegalStateException.class, () ->
                payment.applyProviderResult(new ProviderPaymentResult(
                        "FAKE-005",
                        ProviderPaymentStatus.FAILED
                ))
        );

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("FAKE-005", payment.getProviderReference());
    }

    @Test
    void applyProviderResult_conflictingReference_shouldNotMutateEntity() {
        PaymentEntity payment = newPendingPayment();

        payment.applyProviderResult(new ProviderPaymentResult(
                "FAKE-006",
                ProviderPaymentStatus.SUCCESS
        ));

        assertThrows(IllegalStateException.class, () ->
                payment.applyProviderResult(new ProviderPaymentResult(
                        "FAKE-DIFFERENT",
                        ProviderPaymentStatus.SUCCESS
                ))
        );

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("FAKE-006", payment.getProviderReference());
    }

    @Test
    void applyProviderResult_nullResult_shouldThrow() {
        PaymentEntity payment = newPendingPayment();

        assertThrows(IllegalArgumentException.class, () ->
                payment.applyProviderResult(null)
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getProviderReference());
    }

    @Test
    void applyProviderResult_nullStatus_shouldThrow() {
        PaymentEntity payment = newPendingPayment();

        assertThrows(IllegalArgumentException.class, () ->
                payment.applyProviderResult(new ProviderPaymentResult(
                        "FAKE-007",
                        null
                ))
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getProviderReference());
    }

    @Test
    void applyProviderResult_blankReference_shouldThrow() {
        PaymentEntity payment = newPendingPayment();

        assertThrows(IllegalArgumentException.class, () ->
                payment.applyProviderResult(new ProviderPaymentResult(
                        "   ",
                        ProviderPaymentStatus.SUCCESS
                ))
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getProviderReference());
    }

    @Test
    void applyProviderResult_tooLongReference_shouldThrow() {
        PaymentEntity payment = newPendingPayment();

        assertThrows(IllegalArgumentException.class, () ->
                payment.applyProviderResult(new ProviderPaymentResult(
                        "A".repeat(101),
                        ProviderPaymentStatus.SUCCESS
                ))
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getProviderReference());
    }

}
