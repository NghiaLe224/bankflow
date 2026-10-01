package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.InvalidTransferException;
import com.bankflow.common.exception.TransferNotFoundException;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.mapper.TransferMapper;
import com.bankflow.transfer.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private TransferService transferService;

    @Test
    void createTransfer_shouldCreatePendingTransfer_whenRequestIsValid() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                1L,
                2L,
                new BigDecimal("500000"),
                Currency.VND
        );

        AccountEntity fromAccount = mock(AccountEntity.class);
        AccountEntity toAccount = mock(AccountEntity.class);

        TransferResponse expectedResponse = new TransferResponse(
                10L,
                1L,
                2L,
                new BigDecimal("500000"),
                Currency.VND,
                TransferStatus.PENDING,
                LocalDateTime.of(2026, 10, 1, 18, 30)
        );

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(fromAccount));

        when(accountRepository.findById(2L))
                .thenReturn(Optional.of(toAccount));

        when(transferRepository.saveAndFlush(any(TransferEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(transferMapper.toResponse(any(TransferEntity.class)))
                .thenReturn(expectedResponse);

        // Act
        TransferResponse actualResponse =
                transferService.createTransfer(request);

        // Assert response
        assertSame(expectedResponse, actualResponse);

        // Capture the actual entity created by the service
        ArgumentCaptor<TransferEntity> captor =
                ArgumentCaptor.forClass(TransferEntity.class);

        verify(transferRepository).saveAndFlush(captor.capture());

        TransferEntity transferToSave = captor.getValue();

        assertSame(fromAccount, transferToSave.getFromAccount());
        assertSame(toAccount, transferToSave.getToAccount());

        assertEquals(
                new BigDecimal("500000"),
                transferToSave.getAmount()
        );

        assertEquals(
                Currency.VND,
                transferToSave.getCurrency()
        );

        assertEquals(
                TransferStatus.PENDING,
                transferToSave.getStatus()
        );

        // Verify orchestration
        verify(accountRepository).findById(1L);
        verify(accountRepository).findById(2L);

        verify(entityManager).refresh(transferToSave);
        verify(transferMapper).toResponse(transferToSave);
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

        AccountEntity fromAccount = mock(AccountEntity.class);

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
    void findTransferById_shouldReturnTransferResponse_whenTransferExists() {
        // Arrange
        Long transferId = 10L;

        TransferEntity transfer = mock(TransferEntity.class);

        TransferResponse expectedResponse = new TransferResponse(
                transferId,
                1L,
                2L,
                new BigDecimal("500000"),
                Currency.VND,
                TransferStatus.PENDING,
                LocalDateTime.of(2026, 10, 1, 18, 30)
        );

        when(transferRepository.findById(transferId))
                .thenReturn(Optional.of(transfer));

        when(transferMapper.toResponse(transfer))
                .thenReturn(expectedResponse);

        // Act
        TransferResponse actualResponse =
                transferService.findTransferById(transferId);

        // Assert
        assertSame(expectedResponse, actualResponse);

        verify(transferRepository).findById(transferId);
        verify(transferMapper).toResponse(transfer);

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(entityManager);
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

}
