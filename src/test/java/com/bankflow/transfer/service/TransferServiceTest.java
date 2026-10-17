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
import com.bankflow.transfer.security.TransferAuthorizationPolicy;
import com.bankflow.user.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Mock
    private TransferAuthorizationPolicy transferAuthorizationPolicy;

    @InjectMocks
    private TransferService transferService;


    @Test
    void createTransfer_shouldCompleteTransferAndCreateLedger_whenRequestIsValid() {

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

        UserEntity recipient =
                new UserEntity(
                        "Another User",
                        "recipient@example.com"
                );

        ReflectionTestUtils.setField(
                recipient,
                "id",
                20L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        owner,
                        "123",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "456",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        CreateTransferRequest request =
                new CreateTransferRequest(
                        1L,
                        2L,
                        new BigDecimal("500000"),
                        Currency.VND
                );

        when(accountRepository.findById(
                request.fromAccountId()
        ))
                .thenReturn(Optional.of(fromAccount));

        when(accountRepository.findById(
                request.toAccountId()
        ))
                .thenReturn(Optional.of(toAccount));

        when(transferRepository.saveAndFlush(
                any(TransferEntity.class)
        ))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        TransferResponse expectedResponse =
                new TransferResponse(
                        10L,
                        1L,
                        2L,
                        new BigDecimal("500000"),
                        Currency.VND,
                        TransferStatus.COMPLETED,
                        LocalDateTime.now()
                );

        when(transferMapper.toResponse(
                any(TransferEntity.class)
        ))
                .thenReturn(expectedResponse);


        TransferResponse result =
                transferService.createTransfer(request);


        ArgumentCaptor<TransferEntity> transferCaptor =
                ArgumentCaptor.forClass(
                        TransferEntity.class
                );

        verify(
                transferRepository,
                times(1)
        )
                .saveAndFlush(
                        transferCaptor.capture()
                );

        TransferEntity transfer =
                transferCaptor.getValue();

        assertSame(
                fromAccount,
                transfer.getFromAccount()
        );

        assertSame(
                toAccount,
                transfer.getToAccount()
        );

        assertEquals(
                0,
                request.amount()
                        .compareTo(
                                transfer.getAmount()
                        )
        );

        assertEquals(
                request.currency(),
                transfer.getCurrency()
        );

        assertEquals(
                TransferStatus.COMPLETED,
                transfer.getStatus()
        );

        assertEquals(
                0,
                fromAccount.getBalance()
                        .compareTo(
                                new BigDecimal("4500000")
                        )
        );

        assertEquals(
                0,
                toAccount.getBalance()
                        .compareTo(
                                new BigDecimal("5500000")
                        )
        );

        verify(transferAuthorizationPolicy)
                .assertCanCreateFrom(fromAccount);

        verify(ledgerService)
                .createBalancedEntries(transfer);

        verify(entityManager)
                .flush();

        verify(entityManager)
                .refresh(transfer);

        verify(transferMapper)
                .toResponse(transfer);

        assertSame(
                expectedResponse,
                result
        );
    }


    @Test
    void createTransfer_shouldThrowInvalidTransferException_whenSourceAndDestinationAreSame() {

        CreateTransferRequest request =
                new CreateTransferRequest(
                        1L,
                        1L,
                        new BigDecimal("500000"),
                        Currency.VND
                );


        InvalidTransferException exception =
                assertThrows(
                        InvalidTransferException.class,
                        () -> transferService
                                .createTransfer(request)
                );


        assertEquals(
                "Source and destination accounts must be different",
                exception.getMessage()
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transferRepository);
        verifyNoInteractions(entityManager);
        verifyNoInteractions(transferMapper);
        verifyNoInteractions(transferAuthorizationPolicy);
    }


    @Test
    void createTransfer_shouldThrowAccountNotFoundException_whenSourceAccountDoesNotExist() {

        CreateTransferRequest request =
                new CreateTransferRequest(
                        999L,
                        2L,
                        new BigDecimal("500000"),
                        Currency.VND
                );

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());


        assertThrows(
                AccountNotFoundException.class,
                () -> transferService
                        .createTransfer(request)
        );


        verify(accountRepository)
                .findById(999L);

        verify(
                accountRepository,
                never()
        )
                .findById(2L);

        verify(
                transferRepository,
                never()
        )
                .saveAndFlush(
                        any(TransferEntity.class)
                );

        verifyNoInteractions(
                transferAuthorizationPolicy
        );

        verifyNoInteractions(entityManager);
        verifyNoInteractions(transferMapper);
    }


    @Test
    void createTransfer_shouldThrowAccountNotFoundException_whenDestinationAccountDoesNotExist() {

        CreateTransferRequest request =
                new CreateTransferRequest(
                        1L,
                        999L,
                        new BigDecimal("500000"),
                        Currency.VND
                );

        UserEntity owner =
                new UserEntity(
                        "Le Trong Nghia",
                        "nghia@example.com"
                );

        ReflectionTestUtils.setField(
                owner,
                "id",
                1L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        owner,
                        "123456",
                        new BigDecimal("50000000"),
                        AccountStatus.ACTIVE
                );

        when(accountRepository.findById(1L))
                .thenReturn(
                        Optional.of(fromAccount)
                );

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());


        assertThrows(
                AccountNotFoundException.class,
                () -> transferService
                        .createTransfer(request)
        );


        verify(accountRepository)
                .findById(1L);

        verify(transferAuthorizationPolicy)
                .assertCanCreateFrom(fromAccount);

        verify(accountRepository)
                .findById(999L);

        verify(
                transferRepository,
                never()
        )
                .saveAndFlush(
                        any(TransferEntity.class)
                );

        verifyNoInteractions(entityManager);
        verifyNoInteractions(transferMapper);
    }


    @Test
    void findTransferById_shouldThrowTransferNotFoundException_whenTransferDoesNotExist() {

        Long transferId = 999L;

        when(transferRepository.findById(
                transferId
        ))
                .thenReturn(Optional.empty());


        TransferNotFoundException exception =
                assertThrows(
                        TransferNotFoundException.class,
                        () -> transferService
                                .findTransferById(
                                        transferId
                                )
                );


        assertEquals(
                "Transfer 999 was not found",
                exception.getMessage()
        );

        verify(transferRepository)
                .findById(transferId);

        verifyNoInteractions(
                transferAuthorizationPolicy
        );

        verifyNoInteractions(transferMapper);
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(entityManager);
    }


    @Test
    void createTransfer_shouldThrowForbiddenOperationException_whenAuthorizationPolicyRejectsSourceAccount() {

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

        AccountEntity fromAccount =
                new AccountEntity(
                        owner,
                        "12345678",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        CreateTransferRequest request =
                new CreateTransferRequest(
                        30L,
                        50L,
                        new BigDecimal("100000"),
                        Currency.VND
                );

        when(accountRepository.findById(30L))
                .thenReturn(
                        Optional.of(fromAccount)
                );

        doThrow(
                new ForbiddenOperationException()
        )
                .when(
                        transferAuthorizationPolicy
                )
                .assertCanCreateFrom(
                        fromAccount
                );


        assertThrows(
                ForbiddenOperationException.class,
                () -> transferService
                        .createTransfer(request)
        );


        assertEquals(
                0,
                new BigDecimal("5000000")
                        .compareTo(
                                fromAccount.getBalance()
                        )
        );

        verify(transferAuthorizationPolicy)
                .assertCanCreateFrom(fromAccount);

        verify(
                accountRepository,
                never()
        )
                .findById(50L);

        verify(
                transferRepository,
                never()
        )
                .saveAndFlush(
                        any(TransferEntity.class)
                );

        verifyNoInteractions(ledgerService);
    }


    @Test
    void findTransferById_shouldReturnTransfer_whenAuthorizationPolicyAllowsRecipientTransfer() {

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
                20L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "FROM-001",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "TO-001",
                        new BigDecimal("3000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("500000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        TransferResponse expectedResponse =
                org.mockito.Mockito.mock(
                        TransferResponse.class
                );

        when(transferRepository.findById(100L))
                .thenReturn(
                        Optional.of(transfer)
                );

        when(transferMapper.toResponse(
                transfer
        ))
                .thenReturn(expectedResponse);


        TransferResponse result =
                transferService
                        .findTransferById(100L);


        assertSame(
                expectedResponse,
                result
        );

        verify(transferAuthorizationPolicy)
                .assertCanRead(transfer);

        verify(transferMapper)
                .toResponse(transfer);
    }


    @Test
    void findTransferById_shouldReturnTransfer_whenAuthorizationPolicyAllowsSenderTransfer() {

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
                20L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "FROM-001",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "TO-001",
                        new BigDecimal("3000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("500000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        TransferResponse expectedResponse =
                org.mockito.Mockito.mock(
                        TransferResponse.class
                );

        when(transferRepository.findById(100L))
                .thenReturn(
                        Optional.of(transfer)
                );

        when(transferMapper.toResponse(
                transfer
        ))
                .thenReturn(expectedResponse);


        TransferResponse result =
                transferService
                        .findTransferById(100L);


        assertSame(
                expectedResponse,
                result
        );

        verify(transferAuthorizationPolicy)
                .assertCanRead(transfer);

        verify(transferMapper)
                .toResponse(transfer);
    }


    @Test
    void findTransferById_shouldThrowForbiddenOperationException_whenAuthorizationPolicyRejectsTransfer() {

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
                20L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "FROM-001",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "TO-001",
                        new BigDecimal("3000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("500000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        when(transferRepository.findById(100L))
                .thenReturn(
                        Optional.of(transfer)
                );

        doThrow(
                new ForbiddenOperationException()
        )
                .when(
                        transferAuthorizationPolicy
                )
                .assertCanRead(transfer);


        assertThrows(
                ForbiddenOperationException.class,
                () -> transferService
                        .findTransferById(100L)
        );


        verify(transferAuthorizationPolicy)
                .assertCanRead(transfer);

        verify(
                transferMapper,
                never()
        )
                .toResponse(
                        any(TransferEntity.class)
                );
    }


    @Test
    void findTransferById_shouldReturnTransfer_whenAuthorizationPolicyAllowsAdminTransfer() {

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
                20L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        sender,
                        "FROM-001",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        AccountEntity toAccount =
                new AccountEntity(
                        recipient,
                        "TO-001",
                        new BigDecimal("3000000"),
                        AccountStatus.ACTIVE
                );

        TransferEntity transfer =
                new TransferEntity(
                        fromAccount,
                        toAccount,
                        new BigDecimal("500000"),
                        Currency.VND,
                        TransferStatus.COMPLETED
                );

        TransferResponse expectedResponse =
                org.mockito.Mockito.mock(
                        TransferResponse.class
                );

        when(transferRepository.findById(100L))
                .thenReturn(
                        Optional.of(transfer)
                );

        when(transferMapper.toResponse(
                transfer
        ))
                .thenReturn(expectedResponse);


        TransferResponse result =
                transferService
                        .findTransferById(100L);


        assertSame(
                expectedResponse,
                result
        );

        verify(transferAuthorizationPolicy)
                .assertCanRead(transfer);
    }


    @Test
    void createTransfer_shouldReject_whenAuthorizationPolicyRejectsSourceAccountBeforeDestinationLookup() {

        UserEntity owner =
                new UserEntity(
                        "Owner",
                        "owner@example.com"
                );

        ReflectionTestUtils.setField(
                owner,
                "id",
                40L
        );

        AccountEntity fromAccount =
                new AccountEntity(
                        owner,
                        "FROM-001",
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                );

        CreateTransferRequest request =
                new CreateTransferRequest(
                        10L,
                        20L,
                        new BigDecimal("500000"),
                        Currency.VND
                );

        when(accountRepository.findById(10L))
                .thenReturn(
                        Optional.of(fromAccount)
                );

        doThrow(
                new ForbiddenOperationException()
        )
                .when(
                        transferAuthorizationPolicy
                )
                .assertCanCreateFrom(
                        fromAccount
                );


        assertThrows(
                ForbiddenOperationException.class,
                () -> transferService
                        .createTransfer(request)
        );


        verify(transferAuthorizationPolicy)
                .assertCanCreateFrom(fromAccount);

        verify(
                accountRepository,
                never()
        )
                .findById(20L);

        verify(
                transferRepository,
                never()
        )
                .saveAndFlush(
                        any(TransferEntity.class)
                );

        verifyNoInteractions(ledgerService);
    }


    @Test
    void getTransferHistory_shouldUseCurrentUserScope_whenCurrentUserCannotViewAllHistory() {

        Page<TransferEntity> emptyPage =
                new PageImpl<>(
                        List.of()
                );

        when(
                transferAuthorizationPolicy
                        .canViewAllHistory()
        )
                .thenReturn(false);

        when(currentUserService.getCurrentUserId())
                .thenReturn(10L);

        when(
                transferRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        )
                .thenReturn(emptyPage);


        transferService.getTransferHistory(
                0,
                20,
                null,
                null,
                null,
                null
        );


        verify(transferAuthorizationPolicy)
                .canViewAllHistory();

        verify(currentUserService)
                .getCurrentUserId();

        verify(transferRepository)
                .findAll(
                        any(Specification.class),
                        any(Pageable.class)
                );
    }


    @Test
    void getTransferHistory_shouldNotReadCurrentUserId_whenCurrentUserCanViewAllHistory() {

        Page<TransferEntity> emptyPage =
                new PageImpl<>(
                        List.of()
                );

        when(
                transferAuthorizationPolicy
                        .canViewAllHistory()
        )
                .thenReturn(true);

        when(
                transferRepository.findAll(
                        any(Specification.class),
                        any(Pageable.class)
                )
        )
                .thenReturn(emptyPage);


        transferService.getTransferHistory(
                0,
                20,
                null,
                null,
                null,
                null
        );


        verify(transferAuthorizationPolicy)
                .canViewAllHistory();

        verify(
                currentUserService,
                never()
        )
                .getCurrentUserId();

        verify(transferRepository)
                .findAll(
                        any(Specification.class),
                        any(Pageable.class)
                );
    }
}
