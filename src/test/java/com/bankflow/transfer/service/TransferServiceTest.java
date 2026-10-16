package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.InvalidTransferException;
import com.bankflow.common.exception.TransferNotFoundException;
import com.bankflow.ledger.service.LedgerService;
import com.bankflow.security.CurrentUserService;
import com.bankflow.security.exception.ForbiddenOperationException;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.mapper.TransferMapper;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.user.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TransferMapper transferMapper;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private TransferService transferService;

    @Test
    void createTransfer_shouldCompleteTransferAndCreateLedger_whenRequestIsValid() {
        UserEntity owner = new UserEntity(
                "Le Trong Nghia",
                "nghia@example.com"
        );

        ReflectionTestUtils.setField(
                owner,
                "id",
                10L
        );

        UserEntity recipient = new UserEntity(
                "Another User",
                "recipient@example.com"
        );

        ReflectionTestUtils.setField(
                recipient,
                "id",
                20L
        );

        AccountEntity fromAccount = new AccountEntity(
                owner,
                "123",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );

        AccountEntity toAccount = new AccountEntity(
                recipient,
                "456",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );

        CreateTransferRequest request = new CreateTransferRequest(
                1L,
                2L,
                new BigDecimal("500000"),
                Currency.VND
        );

        when(accountRepository.findById(request.fromAccountId())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(request.toAccountId())).thenReturn(Optional.of(toAccount));
        when(transferRepository.saveAndFlush(any(TransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        TransferResponse expectedResponse = new TransferResponse(
                10L,
                1L,
                2L,
                new BigDecimal("500000"),
                Currency.VND,
                TransferStatus.COMPLETED,
                LocalDateTime.now()
        );

        when(transferMapper.toResponse(any(TransferEntity.class))).thenReturn(expectedResponse);

        //act
        TransferResponse result = transferService.createTransfer(request);

        //assert
        ArgumentCaptor<TransferEntity> transferCaptor = ArgumentCaptor.forClass(TransferEntity.class);
        verify(transferRepository, times(1)).saveAndFlush(transferCaptor.capture());
        TransferEntity transfer = transferCaptor.getValue();

        assertSame(fromAccount, transfer.getFromAccount());
        assertSame(toAccount, transfer.getToAccount());

        assertEquals(
                0,
                request.amount().compareTo(transfer.getAmount())
        );

        assertEquals(request.currency(), transfer.getCurrency());

        assertEquals(
                TransferStatus.COMPLETED,
                transfer.getStatus()
        );

        assertEquals(
                0,
                fromAccount.getBalance()
                        .compareTo(new BigDecimal("4500000"))
        );

        assertEquals(
                0,
                toAccount.getBalance()
                        .compareTo(new BigDecimal("5500000"))
        );

        verify(ledgerService)
                .createBalancedEntries(transfer);

        verify(entityManager).flush();
        verify(entityManager).refresh(transfer);

        verify(transferMapper).toResponse(transfer);

        assertSame(expectedResponse, result);
        verify(currentUserService)
                .getCurrentUserId();
    }

    @Test
    void createTransfer_shouldThrowInvalidTransferException_whenSourceAndDestinationAreSame() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                1L,
                1L,
                new BigDecimal("500000"),
                Currency.VND
        );

        // Act
        InvalidTransferException exception = assertThrows(
                InvalidTransferException.class,
                () -> transferService.createTransfer(request)
        );

        // Assert
        assertEquals(
                "Source and destination accounts must be different",
                exception.getMessage()
        );

        // Validation fails before touching persistence
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transferRepository);
        verifyNoInteractions(entityManager);
        verifyNoInteractions(transferMapper);
    }

    @Test
    void createTransfer_shouldThrowAccountNotFoundException_whenSourceAccountDoesNotExist() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                999L,
                2L,
                new BigDecimal("500000"),
                Currency.VND
        );

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(request)
        );

        verify(accountRepository).findById(999L);

        // Destination must not even be queried
        verify(accountRepository, never()).findById(2L);

        // No transfer should be persisted
        verify(transferRepository, never())
                .saveAndFlush(any(TransferEntity.class));

        verifyNoInteractions(entityManager);
        verifyNoInteractions(transferMapper);
    }

    @Test
    void createTransfer_shouldThrowAccountNotFoundException_whenDestinationAccountDoesNotExist() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                1L,
                999L,
                new BigDecimal("500000"),
                Currency.VND
        );

        UserEntity owner = new UserEntity("Le Trong Nghia", "nghia@example.com");

        ReflectionTestUtils.setField(owner, "id", 1L);

        AccountEntity fromAccount = new AccountEntity(
                owner,
                "123456",
                new BigDecimal("50000000"),
                AccountStatus.ACTIVE
        );

        when(currentUserService.getCurrentUserId()).thenReturn(1L);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                AccountNotFoundException.class,
                () -> transferService.createTransfer(request)
        );

        verify(accountRepository).findById(1L);
        verify(accountRepository).findById(999L);

        verify(transferRepository, never())
                .saveAndFlush(any(TransferEntity.class));

        verifyNoInteractions(entityManager);
        verifyNoInteractions(transferMapper);
    }

    @Test
    void findTransferById_shouldThrowTransferNotFoundException_whenTransferDoesNotExist() {
        // Arrange
        Long transferId = 999L;

        when(transferRepository.findById(transferId))
                .thenReturn(Optional.empty());

        // Act
        TransferNotFoundException exception = assertThrows(
                TransferNotFoundException.class,
                () -> transferService.findTransferById(transferId)
        );

        // Assert
        assertEquals(
                "Transfer 999 was not found",
                exception.getMessage()
        );

        verify(transferRepository).findById(transferId);

        // No entity exists, therefore mapping must never happen
        verifyNoInteractions(transferMapper);
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(entityManager);
    }

    @Test
    void createTransfer_shouldThrowForbiddenOperationException_whenCurrentUserDoesNotOwnFromAccount() {
        UserEntity owner = new UserEntity("Le Trong Nghia", "nghia@example.com");
        ReflectionTestUtils.setField(owner, "id", 10L);
        AccountEntity fromAccount = new AccountEntity(
                owner,
                "12345678",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );
        CreateTransferRequest request = new CreateTransferRequest(
                30L,
                50L,
                new BigDecimal("100000"),
                Currency.VND
        );
        when(currentUserService.getCurrentUserId()).thenReturn(20L);
        when(accountRepository.findById(30L)).thenReturn(Optional.of(fromAccount));

        assertThrows(ForbiddenOperationException.class, () -> transferService.createTransfer(request));

        assertEquals(
                0,
                new BigDecimal("5000000")
                        .compareTo(fromAccount.getBalance())
        );

        verify(accountRepository, never())
                .findById(50L);

        verify(transferRepository, never())
                .saveAndFlush(any(TransferEntity.class));

        verifyNoInteractions(ledgerService);
    }

    @Test
    void findTransferById_shouldReturnTransfer_whenCurrentUserIsRecipient() {
        UserEntity sender = new UserEntity(
                "Sender",
                "sender@example.com"
        );
        ReflectionTestUtils.setField(sender, "id", 10L);

        UserEntity recipient = new UserEntity(
                "Recipient",
                "recipient@example.com"
        );
        ReflectionTestUtils.setField(recipient, "id", 20L);

        AccountEntity fromAccount = new AccountEntity(
                sender,
                "FROM-001",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );

        AccountEntity toAccount = new AccountEntity(
                recipient,
                "TO-001",
                new BigDecimal("3000000"),
                AccountStatus.ACTIVE
        );

        TransferEntity transfer = new TransferEntity(
                fromAccount,
                toAccount,
                new BigDecimal("500000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        TransferResponse expectedResponse = mock(TransferResponse.class);

        when(transferRepository.findById(100L))
                .thenReturn(Optional.of(transfer));

        when(currentUserService.getCurrentUserId())
                .thenReturn(20L);

        when(transferMapper.toResponse(transfer))
                .thenReturn(expectedResponse);

        TransferResponse result =
                transferService.findTransferById(100L);

        assertSame(expectedResponse, result);
    }

    @Test
    void findTransferById_shouldReturnTransfer_whenCurrentUserIsSender() {
        UserEntity sender = new UserEntity(
                "Sender",
                "sender@example.com"
        );
        ReflectionTestUtils.setField(sender, "id", 10L);

        UserEntity recipient = new UserEntity(
                "Recipient",
                "recipient@example.com"
        );
        ReflectionTestUtils.setField(recipient, "id", 20L);

        AccountEntity fromAccount = new AccountEntity(
                sender,
                "FROM-001",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );

        AccountEntity toAccount = new AccountEntity(
                recipient,
                "TO-001",
                new BigDecimal("3000000"),
                AccountStatus.ACTIVE
        );

        TransferEntity transfer = new TransferEntity(
                fromAccount,
                toAccount,
                new BigDecimal("500000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        TransferResponse expectedResponse = mock(TransferResponse.class);

        when(transferRepository.findById(100L))
                .thenReturn(Optional.of(transfer));

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        when(transferMapper.toResponse(transfer))
                .thenReturn(expectedResponse);

        TransferResponse result =
                transferService.findTransferById(100L);

        assertSame(expectedResponse, result);
    }

    @Test
    void findTransferById_shouldThrowForbiddenOperationException_whenCurrentUserIsUnrelated() {
        UserEntity sender = new UserEntity(
                "Sender",
                "sender@example.com"
        );
        ReflectionTestUtils.setField(sender, "id", 10L);

        UserEntity recipient = new UserEntity(
                "Recipient",
                "recipient@example.com"
        );
        ReflectionTestUtils.setField(recipient, "id", 20L);

        AccountEntity fromAccount = new AccountEntity(
                sender,
                "FROM-001",
                new BigDecimal("5000000"),
                AccountStatus.ACTIVE
        );

        AccountEntity toAccount = new AccountEntity(
                recipient,
                "TO-001",
                new BigDecimal("3000000"),
                AccountStatus.ACTIVE
        );

        TransferEntity transfer = new TransferEntity(
                fromAccount,
                toAccount,
                new BigDecimal("500000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        when(transferRepository.findById(100L))
                .thenReturn(Optional.of(transfer));

        when(currentUserService.getCurrentUserId())
                .thenReturn(30L);

        assertThrows(
                ForbiddenOperationException.class,
                () -> transferService.findTransferById(100L)
        );

        verify(transferMapper, never())
                .toResponse(any(TransferEntity.class));
    }

}
