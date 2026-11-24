package com.bankflow.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static com.bankflow.payment.domain.PaymentStatus.*;

class PaymentStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, SUCCESS, true",
            "PENDING, FAILED, true",
            "PENDING, UNKNOWN, true",
            "UNKNOWN, SUCCESS, true",
            "UNKNOWN, FAILED, true",
            "SUCCESS, FAILED, false",
            "FAILED, SUCCESS, false",
            "SUCCESS, SUCCESS, true",
            "UNKNOWN, UNKNOWN, true",
            "PENDING, PENDING, true",
            "FAILED, FAILED, true",
            "SUCCESS, UNKNOWN, false",
            "FAILED, UNKNOWN, false",
            "UNKNOWN, PENDING, false",
            "SUCCESS, PENDING, false",
            "FAILED, PENDING, false"
    })
    void shouldValidatePaymentTransitions(
            PaymentStatus current,
            PaymentStatus next,
            boolean expected
    ) {
        boolean actual = PaymentStateMachine.canTransition(
                current,
                next
        );

        assertEquals(expected, actual);
    }

    @Test
    void shouldRejectNullStates() {
        assertFalse(PaymentStateMachine.canTransition(null, PENDING));
        assertFalse(PaymentStateMachine.canTransition(PENDING, null));
        assertFalse(PaymentStateMachine.canTransition(null, null));
    }
}
