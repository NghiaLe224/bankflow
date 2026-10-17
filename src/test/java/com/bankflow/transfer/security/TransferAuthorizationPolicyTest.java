package com.bankflow.transfer.security;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.security.CurrentUserService;
import com.bankflow.security.exception.ForbiddenOperationException;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferAuthorizationPolicyTest {

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private TransferAuthorizationPolicy transferAuthorizationPolicy;


    @Test
    void assertCanCreateFrom_shouldAllow_whenCurrentUserOwnsSourceAccount() {

        UserEntity owner =
                new UserEntity(
                        "Owner",
                        "owner@example.com"
                );

        ReflectionTestUtils.setField(
                owner,
                "id",
                10L
        );

        AccountEntity account =
                new AccountEntity(
                        owner,
                        "ACC100",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);


        transferAuthorizationPolicy
                .assertCanCreateFrom(account);


        verify(currentUserService)
                .getCurrentUserId();

        verify(
                currentUserService,
                never()
        )
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void assertCanCreateFrom_shouldThrowForbidden_whenCurrentUserDoesNotOwnSourceAccount() {

        UserEntity owner =
                new UserEntity(
                        "Owner",
                        "owner@example.com"
                );

        ReflectionTestUtils.setField(
                owner,
                "id",
                10L
        );

        AccountEntity account =
                new AccountEntity(
                        owner,
                        "ACC100",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(20L);


        assertThrows(
                ForbiddenOperationException.class,
                () -> transferAuthorizationPolicy
                        .assertCanCreateFrom(account)
        );


        verify(currentUserService)
                .getCurrentUserId();

        verify(
                currentUserService,
                never()
        )
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void assertCanRead_shouldAllow_whenCurrentUserIsParticipant() {

        UserEntity sender =
                new UserEntity(
                        "Sender",
                        "sender@example.com"
                );

        ReflectionTestUtils.setField(
                sender,
                "id",
                10L
        );

        UserEntity recipient =
                new UserEntity(
                        "Recipient",
                        "recipient@example.com"
                );

        ReflectionTestUtils.setField(
                recipient,
                "id",
                11L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "ACC001",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "ACC002",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("200000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(11L);


        transferAuthorizationPolicy
                .assertCanRead(transfer);


        verify(currentUserService)
                .getCurrentUserId();

        verify(
                currentUserService,
                never()
        )
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void assertCanRead_shouldThrowForbidden_whenCurrentUserIsNotParticipantOrAdmin() {

        UserEntity sender =
                new UserEntity(
                        "Sender",
                        "sender@example.com"
                );

        ReflectionTestUtils.setField(
                sender,
                "id",
                10L
        );

        UserEntity recipient =
                new UserEntity(
                        "Recipient",
                        "recipient@example.com"
                );

        ReflectionTestUtils.setField(
                recipient,
                "id",
                11L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "ACC001",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "ACC002",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("200000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(15L);

        when(currentUserService.hasRole(UserRole.ADMIN))
                .thenReturn(false);


        assertThrows(
                ForbiddenOperationException.class,
                () -> transferAuthorizationPolicy
                        .assertCanRead(transfer)
        );


        verify(currentUserService)
                .getCurrentUserId();

        verify(currentUserService)
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void assertCanRead_shouldAllow_whenCurrentUserIsAdminAndNotParticipant() {

        UserEntity sender =
                new UserEntity(
                        "Sender",
                        "sender@example.com"
                );

        ReflectionTestUtils.setField(
                sender,
                "id",
                10L
        );

        UserEntity recipient =
                new UserEntity(
                        "Recipient",
                        "recipient@example.com"
                );

        ReflectionTestUtils.setField(
                recipient,
                "id",
                11L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "ACC001",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "ACC002",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("200000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(99L);

        when(currentUserService.hasRole(UserRole.ADMIN))
                .thenReturn(true);


        transferAuthorizationPolicy
                .assertCanRead(transfer);


        verify(currentUserService)
                .getCurrentUserId();

        verify(currentUserService)
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void canViewAllHistory_shouldReturnTrue_whenCurrentUserIsAdmin() {

        when(currentUserService.hasRole(UserRole.ADMIN))
                .thenReturn(true);


        boolean result =
                transferAuthorizationPolicy
                        .canViewAllHistory();


        assertTrue(result);

        verify(currentUserService)
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void canViewAllHistory_shouldReturnFalse_whenCurrentUserIsNotAdmin() {

        when(currentUserService.hasRole(UserRole.ADMIN))
                .thenReturn(false);


        boolean result =
                transferAuthorizationPolicy
                        .canViewAllHistory();


        assertFalse(result);

        verify(currentUserService)
                .hasRole(UserRole.ADMIN);
    }
}
