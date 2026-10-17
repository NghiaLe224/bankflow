package com.bankflow.account.service;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.security.AccountAuthorizationPolicy;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.UserNotFoundException;
import com.bankflow.security.CurrentUserService;
import com.bankflow.security.exception.ForbiddenOperationException;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountAuthorizationPolicy accountAuthorizationPolicy;

    @InjectMocks
    private AccountService accountService;


    @Test
    void createAccount_shouldCreateZeroBalanceAccountForAuthenticatedUser_whenUserExists() {

        CreateAccountRequest request =
                new CreateAccountRequest();

        request.setAccountNumber("ACC001");

        UserEntity user =
                new UserEntity(
                        "Le Trong Nghia",
                        "nghia@gmail.com"
                );

        ReflectionTestUtils.setField(
                user,
                "id",
                10L
        );

        AccountEntity savedAccount =
                mock(AccountEntity.class);

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        when(accountRepository.save(
                any(AccountEntity.class)
        ))
                .thenReturn(savedAccount);

        when(savedAccount.getId())
                .thenReturn(100L);

        when(savedAccount.getUser())
                .thenReturn(user);

        when(savedAccount.getBalance())
                .thenReturn(BigDecimal.ZERO);

        when(savedAccount.getStatus())
                .thenReturn(AccountStatus.ACTIVE);


        AccountResponse response =
                accountService.createAccount(request);


        assertEquals(
                100L,
                response.getId()
        );

        assertEquals(
                "Le Trong Nghia",
                response.getOwner()
        );

        assertEquals(
                BigDecimal.ZERO,
                response.getBalance()
        );

        assertEquals(
                "ACTIVE",
                response.getStatus()
        );


        ArgumentCaptor<AccountEntity> captor =
                ArgumentCaptor.forClass(
                        AccountEntity.class
                );

        verify(currentUserService)
                .getCurrentUserId();

        verify(userRepository)
                .findById(10L);

        verify(accountRepository)
                .save(captor.capture());


        AccountEntity accountToSave =
                captor.getValue();

        assertSame(
                user,
                accountToSave.getUser()
        );

        assertEquals(
                10L,
                accountToSave
                        .getUser()
                        .getId()
        );

        assertEquals(
                "ACC001",
                accountToSave.getAccountNumber()
        );

        assertEquals(
                BigDecimal.ZERO,
                accountToSave.getBalance()
        );

        assertEquals(
                AccountStatus.ACTIVE,
                accountToSave.getStatus()
        );
    }


    @Test
    void createAccount_shouldThrowException_whenAuthenticatedUserDoesNotExist() {

        CreateAccountRequest request =
                new CreateAccountRequest();

        request.setAccountNumber("ACC001");

        when(currentUserService.getCurrentUserId())
                .thenReturn(999L);

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());


        UserNotFoundException exception =
                assertThrows(
                        UserNotFoundException.class,
                        () -> accountService
                                .createAccount(request)
                );


        assertEquals(
                "User 999 was not found",
                exception.getMessage()
        );

        verify(currentUserService)
                .getCurrentUserId();

        verify(userRepository)
                .findById(999L);

        verify(
                accountRepository,
                never()
        )
                .save(any(AccountEntity.class));
    }


    @Test
    void getAccount_shouldThrowException_whenAccountDoesNotExist() {

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());


        AccountNotFoundException exception =
                assertThrows(
                        AccountNotFoundException.class,
                        () -> accountService
                                .getAccount(999L)
                );


        assertEquals(
                "Account 999 was not found",
                exception.getMessage()
        );

        verify(accountRepository)
                .findById(999L);

        verifyNoInteractions(
                currentUserService
        );
    }


    @Test
    void getAccount_shouldReturnAccount_whenCurrentUserIsOwner() {
        UserEntity owner =
                new UserEntity(
                        "Le Trong Nghia",
                        "nghia@example.com"
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
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );
        ReflectionTestUtils.setField(
                account,
                "id",
                100L
        );
        when(accountRepository.findById(100L))
                .thenReturn(Optional.of(account));

        AccountResponse response =
                accountService.getAccount(100L);

        assertEquals(
                100L,
                response.getId()
        );
        assertEquals(
                "Le Trong Nghia",
                response.getOwner()
        );
        assertEquals(
                new BigDecimal("5000000"),
                response.getBalance()
        );
        assertEquals(
                "ACTIVE",
                response.getStatus()
        );
        verify(accountRepository)
                .findById(100L);
        verify(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));

    }


    @Test
    void getAccount_shouldReturnAccount_whenCurrentUserIsAdminAndNotOwner() {

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
                        "ACC200",
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                );
        ReflectionTestUtils.setField(
                account,
                "id",
                200L
        );
        when(accountRepository.findById(200L))
                .thenReturn(Optional.of(account));

        AccountResponse response =
                accountService.getAccount(200L);

        assertEquals(
                200L,
                response.getId()
        );
        assertEquals(
                new BigDecimal("1000000"),
                response.getBalance()
        );
        verify(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));
    }


    @Test
    void getAccount_shouldThrowForbiddenOperationException_whenCurrentUserIsNotOwnerOrAdmin() {

        UserEntity owner =
                new UserEntity(
                        "Another User",
                        "owner@example.com"
                );

        ReflectionTestUtils.setField(
                owner,
                "id",
                20L
        );

        AccountEntity account =
                new AccountEntity(
                        owner,
                        "ACC300",
                        new BigDecimal("3000000"),
                        AccountStatus.ACTIVE
                );

        ReflectionTestUtils.setField(
                account,
                "id",
                300L
        );

        when(accountRepository.findById(300L))
                .thenReturn(Optional.of(account));

        doThrow(new ForbiddenOperationException())
                .when(accountAuthorizationPolicy)
                .assertCanAccess(account);

        assertThrows(
                ForbiddenOperationException.class,
                () -> accountService
                        .getAccount(300L)
        );


        verify(accountAuthorizationPolicy).assertCanAccess(any(AccountEntity.class));
    }


    @Test
    void blockAccount_shouldBlockAccount_whenCurrentUserIsOwner() {
        UserEntity owner =
                new UserEntity(
                        "Le Trong Nghia",
                        "nghia@example.com"
                );
        ReflectionTestUtils.setField(
                owner,
                "id",
                10L
        );
        AccountEntity account =
                new AccountEntity(
                        owner,
                        "ACC400",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );
        ReflectionTestUtils.setField(
                account,
                "id",
                400L
        );
        when(accountRepository.findById(400L))
                .thenReturn(Optional.of(account));


        accountService.blockAccount(400L);


        assertEquals(
                AccountStatus.BLOCKED,
                account.getStatus()
        );

        verify(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));
    }

    @Test
    void blockAccount_shouldThrowForbiddenOperationException_whenCurrentUserIsNotOwnerOrAdmin() {
        UserEntity owner =
                new UserEntity(
                        "Another User",
                        "owner@example.com"
                );
        ReflectionTestUtils.setField(
                owner,
                "id",
                20L
        );
        AccountEntity account =
                new AccountEntity(
                        owner,
                        "ACC600",
                        new BigDecimal("3000000"),
                        AccountStatus.ACTIVE
                );
        ReflectionTestUtils.setField(
                account,
                "id",
                600L
        );
        when(accountRepository.findById(600L))
                .thenReturn(Optional.of(account));
        doThrow(new ForbiddenOperationException())
                .when(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));

        assertThrows(
                ForbiddenOperationException.class,
                () -> accountService
                        .blockAccount(600L)
        );

        assertEquals(
                AccountStatus.ACTIVE,
                account.getStatus()
        );
        verify(accountAuthorizationPolicy).assertCanAccess(any(AccountEntity.class));
    }

    @Test
    void getByAccountNumber_shouldReturnAccountResponse_whenCurrentUserIsOwner() {
        UserEntity user = new UserEntity("user", "user@example.com");
        ReflectionTestUtils.setField(user, "id", 10L);

        AccountEntity account = new AccountEntity(
                user,
                "abcxyz123",
                new BigDecimal("1000000"),
                AccountStatus.ACTIVE
        );
        when(accountRepository.findByAccountNumber("abcxyz123")).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getByAccountNumber("abcxyz123");

        assertEquals(0, response.getBalance().compareTo(account.getBalance()));
        verify(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));
    }

    @Test
    void getByAccountNumber_shouldThrowForbidden_whenCurrentUserIsNotOwnerOrAdmin() {
        UserEntity user = new UserEntity("user", "user@example.com");
        ReflectionTestUtils.setField(user, "id", 10L);

        AccountEntity account = new AccountEntity(
                user,
                "abcxyz123",
                new BigDecimal("1000000"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByAccountNumber("abcxyz123")).thenReturn(Optional.of(account));
        doThrow(new ForbiddenOperationException())
                .when(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));

        assertThrows(ForbiddenOperationException.class,
                () -> accountService.getByAccountNumber("abcxyz123"));

        verify(accountAuthorizationPolicy)
                .assertCanAccess(any(AccountEntity.class));
    }

    @Test
    void getCurrentUserAccounts_shouldReturnAccountsOfAuthenticatedUser() {
        UserEntity user = new UserEntity(
                "username",
                "username@example.com"
        );
        AccountEntity account1 = new AccountEntity(
                user,
                "test1account",
                new BigDecimal("1000000"),
                AccountStatus.ACTIVE
        );
        AccountEntity account2 = new AccountEntity(
                user,
                "test2account",
                new BigDecimal("1000000"),
                AccountStatus.ACTIVE
        );

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);
        when(accountRepository.findByUserId(10L))
                .thenReturn(List.of(account1, account2));

        List<AccountResponse> accounts = accountService.getCurrentUserAccounts();

        assertEquals(2, accounts.size());
        assertEquals("username", accounts.getFirst().getOwner());
        assertEquals("username", accounts.get(1).getOwner());
        verify(accountRepository)
                .findByUserId(10L);

        verify(currentUserService)
                .getCurrentUserId();
    }
}
