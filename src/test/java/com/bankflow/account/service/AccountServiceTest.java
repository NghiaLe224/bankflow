package com.bankflow.account.service;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    CurrentUserService currentUserService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void getAccount_shouldReturnAccount_whenAccountExists() {
        AccountEntity account = mock(AccountEntity.class);
        UserEntity user = mock(UserEntity.class);

        when(account.getId()).thenReturn(1L);
        when(account.getUser()).thenReturn(user);
        when(user.getFullName()).thenReturn("Le Trong Nghia");
        when(account.getBalance()).thenReturn(new BigDecimal("1000000"));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccount(1L);

        assertEquals(1L, response.getId());
        assertEquals("Le Trong Nghia", response.getOwner());
        assertEquals(new BigDecimal("1000000"), response.getBalance());
        assertEquals("ACTIVE", response.getStatus());
        verify(accountRepository).findById(1L);
    }

    @Test
    void getAccount_shouldThrowException_whenAccountDoesNotExist() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        AccountNotFoundException exception = assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getAccount(999L)
        );

        assertEquals(
                "Account 999 was not found",
                exception.getMessage()
        );
        verify(accountRepository).findById(999L);
    }

    @Test
    void createAccount_shouldReturnAccountResponse_whenUserExists() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setUserId(1L);
        request.setAccountNumber("ACC001");
        request.setInitialBalance(new BigDecimal("5000000"));

        UserEntity user = new UserEntity(
                "Le Trong Nghia",
                "nghia@gmail.com"
        );

        AccountEntity savedAccount = mock(AccountEntity.class);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(accountRepository.save(any(AccountEntity.class)))
                .thenReturn(savedAccount);

        when(savedAccount.getId()).thenReturn(10L);
        when(savedAccount.getUser()).thenReturn(user);
        when(savedAccount.getBalance())
                .thenReturn(new BigDecimal("5000000"));
        when(savedAccount.getStatus())
                .thenReturn(AccountStatus.ACTIVE);

        AccountResponse response = accountService.createAccount(request);

        assertEquals(10L, response.getId());
        assertEquals("Le Trong Nghia", response.getOwner());
        assertEquals(
                new BigDecimal("5000000"),
                response.getBalance()
        );
        assertEquals("ACTIVE", response.getStatus());

        ArgumentCaptor<AccountEntity> captor =
                ArgumentCaptor.forClass(AccountEntity.class);

        verify(userRepository).findById(1L);
        verify(accountRepository).save(captor.capture());

        AccountEntity accountToSave = captor.getValue();

        assertEquals("ACC001", accountToSave.getAccountNumber());
        assertEquals(
                new BigDecimal("5000000"),
                accountToSave.getBalance()
        );
        assertEquals(AccountStatus.ACTIVE, accountToSave.getStatus());
        assertSame(user, accountToSave.getUser());
    }

    @Test
    void createAccount_shouldThrowException_whenUserDoesNotExist() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest();
        request.setUserId(999L);
        request.setAccountNumber("ACC001");
        request.setInitialBalance(new BigDecimal("5000000"));

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> accountService.createAccount(request)
        );

        assertEquals(
                "User 999 was not found",
                exception.getMessage()
        );

        verify(userRepository).findById(999L);
        verify(accountRepository, never())
                .save(any(AccountEntity.class));
    }

    @Test
    void getAccount_shouldReturnAccount_whenCurrentUserIsOwner() {
        UserEntity owner = new UserEntity(
                "Le Trong Nghia",
                "nghia@example.com"
        );

        ReflectionTestUtils.setField(
                owner,
                "id",
                10L
        );

        AccountEntity account = new AccountEntity(
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

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        AccountResponse response =
                accountService.getAccount(100L);

        assertEquals(
                100L,
                response.getId()
        );

        verify(accountRepository)
                .findById(100L);

        verify(currentUserService)
                .getCurrentUserId();
    }

    @Test
    void getAccount_shouldThrowForbiddenOperationException_whenCurrentUserIsNotOwner() {
        UserEntity owner = new UserEntity(
                "Another User",
                "owner@example.com"
        );

        ReflectionTestUtils.setField(
                owner,
                "id",
                20L
        );

        AccountEntity account = new AccountEntity(
                owner,
                "ACC200",
                new BigDecimal("3000000"),
                AccountStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                account,
                "id",
                200L
        );

        when(accountRepository.findById(200L))
                .thenReturn(Optional.of(account));

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        assertThrows(
                ForbiddenOperationException.class,
                () -> accountService.getAccount(200L)
        );

        verify(accountRepository)
                .findById(200L);

        verify(currentUserService)
                .getCurrentUserId();
    }

    @Test
    void blockAccount_shouldBlockAccount_whenCurrentUserIsOwner() {
        UserEntity owner = new UserEntity(
                "Le Trong Nghia",
                "nghia@example.com"
        );

        ReflectionTestUtils.setField(
                owner,
                "id",
                10L
        );

        AccountEntity account = new AccountEntity(
                owner,
                "ACC300",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                account,
                "id",
                300L
        );

        when(accountRepository.findById(300L))
                .thenReturn(Optional.of(account));

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        accountService.blockAccount(300L);

        assertEquals(
                AccountStatus.BLOCKED,
                account.getStatus()
        );

        verify(currentUserService)
                .getCurrentUserId();
    }

    @Test
    void blockAccount_shouldThrowForbiddenOperationException_whenCurrentUserIsNotOwner() {
        UserEntity owner = new UserEntity(
                "Another User",
                "owner@example.com"
        );

        ReflectionTestUtils.setField(
                owner,
                "id",
                20L
        );

        AccountEntity account = new AccountEntity(
                owner,
                "ACC400",
                new BigDecimal("3000000"),
                AccountStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                account,
                "id",
                400L
        );

        when(accountRepository.findById(400L))
                .thenReturn(Optional.of(account));

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        assertThrows(
                ForbiddenOperationException.class,
                () -> accountService.blockAccount(400L)
        );

        assertEquals(
                AccountStatus.ACTIVE,
                account.getStatus()
        );
    }
}
