package com.bankflow.account.entity;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.user.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountEntityTest {

    @Test
    void debit_shouldDecreaseBalance_whenAmountIsValid() {
        AccountEntity account = createAccount("5000000");

        account.debit(new BigDecimal("500000"));

        assertEquals(
                0,
                new BigDecimal("4500000")
                        .compareTo(account.getBalance())
        );
    }

    @Test
    void debit_shouldReject_whenAmountIsZero() {
        AccountEntity account = createAccount("5000000");

        assertThrows(
                IllegalArgumentException.class,
                () -> account.debit(BigDecimal.ZERO)
        );

        assertEquals(
                0,
                new BigDecimal("5000000")
                        .compareTo(account.getBalance())
        );
    }

    @Test
    void debit_shouldReject_whenAmountIsNegative() {
        AccountEntity account = createAccount("5000000");

        assertThrows(
                IllegalArgumentException.class,
                () -> account.debit(new BigDecimal("-100000"))
        );

        assertEquals(
                0,
                new BigDecimal("5000000")
                        .compareTo(account.getBalance())
        );
    }

    @Test
    void debit_shouldReject_whenBalanceIsInsufficient() {
        AccountEntity account = createAccount("100000");

        assertThrows(
                InsufficientFundsException.class,
                () -> account.debit(new BigDecimal("500000"))
        );

        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(account.getBalance())
        );
    }

    private AccountEntity createAccount(String balance) {
        UserEntity user = new UserEntity(
                "Domain Test User",
                "domain@test.com"
        );

        return new AccountEntity(
                user,
                "DOMAIN-ACC",
                new BigDecimal(balance),
                AccountStatus.ACTIVE
        );
    }
}
