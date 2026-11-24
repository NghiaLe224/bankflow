package com.bankflow.payment.domain;

public final class PaymentStateMachine {

    private PaymentStateMachine() {}

    public static boolean canTransition(
            PaymentStatus current,
            PaymentStatus next
    ) {
        if (current == null || next == null) {
            return false;
        }

        if (current == next) {
            return true;
        }

        return switch (current) {
            case PENDING -> next == PaymentStatus.SUCCESS
                    || next == PaymentStatus.FAILED
                    || next == PaymentStatus.UNKNOWN;

            case UNKNOWN -> next == PaymentStatus.SUCCESS
                    || next == PaymentStatus.FAILED;

            case SUCCESS, FAILED -> false;
        };
    }
}
