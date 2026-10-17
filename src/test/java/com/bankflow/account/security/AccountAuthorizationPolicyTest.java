package com.bankflow.account.security;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.security.CurrentUserService;
import com.bankflow.security.exception.ForbiddenOperationException;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountAuthorizationPolicyTest {

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AccountAuthorizationPolicy accountAuthorizationPolicy;


    @Test
    void assertCanAccess_shouldAllow_whenCurrentUserIsOwner() {

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
                        "ACC001",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);


        assertDoesNotThrow(
                () -> accountAuthorizationPolicy
                        .assertCanAccess(account)
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
    void assertCanAccess_shouldAllow_whenCurrentUserIsAdminAndNotOwner() {

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
                        "ACC001",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(99L);

        when(currentUserService.hasRole(
                UserRole.ADMIN
        ))
                .thenReturn(true);


        assertDoesNotThrow(
                () -> accountAuthorizationPolicy
                        .assertCanAccess(account)
        );


        verify(currentUserService)
                .getCurrentUserId();

        verify(currentUserService)
                .hasRole(UserRole.ADMIN);
    }


    @Test
    void assertCanAccess_shouldThrowForbiddenOperationException_whenCurrentUserIsNotOwnerOrAdmin() {

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
                        "ACC001",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );

        when(currentUserService.getCurrentUserId())
                .thenReturn(99L);

        when(currentUserService.hasRole(
                UserRole.ADMIN
        ))
                .thenReturn(false);


        assertThrows(
                ForbiddenOperationException.class,
                () -> accountAuthorizationPolicy
                        .assertCanAccess(account)
        );


        verify(currentUserService)
                .getCurrentUserId();

        verify(currentUserService)
                .hasRole(UserRole.ADMIN);
    }
}
