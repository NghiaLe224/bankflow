package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.common.exception.TransferNotFoundException;
import com.bankflow.ledger.entity.LedgerEntryEntity;
import com.bankflow.ledger.enums.LedgerEntryType;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class TransferIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createTransfer_shouldPersistAtomicTransfer_whenRequestIsValid() {
        // Arrange

        // 1. create test user
        // 2. save user
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity savedUser = userRepository.save(
                new UserEntity(
                        "Integration Test User",
                        "integration-" + suffix + "@test.com"
                )
        );

        // 3. create sender balance = 5_000_000
        // 4. create receiver balance = 3_000_000
        // 5. save both accounts
        List<AccountEntity> savedAccounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                savedUser,
                                "TEST-SENDER-" + suffix,
                                new BigDecimal("5000000"),
                                AccountStatus.ACTIVE
                        ),
                        new AccountEntity(
                                savedUser,
                                "TEST-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.ACTIVE
                        )
                )
        );

        AccountEntity fromAccount = savedAccounts.get(0);
        AccountEntity toAccount = savedAccounts.get(1);

        // 6. create request 500_000 VND
        CreateTransferRequest request = new CreateTransferRequest(
                fromAccount.getId(),
                toAccount.getId(),
                new BigDecimal("500000"),
                Currency.VND
        );

        Long transferId = null;

        try {
            // Act
            // transferService.createTransfer(request)
            TransferResponse response = transferService.createTransfer(request);
            transferId = response.id();

            // Assert
            // reload sender
            AccountEntity reloadFromAccount = accountRepository.findById(fromAccount.getId())
                    .orElseThrow(() -> new AccountNotFoundException(fromAccount.getId()));
            // reload receiver
            AccountEntity reloadToAccount = accountRepository.findById(toAccount.getId())
                    .orElseThrow(() -> new AccountNotFoundException(toAccount.getId()));

            // assert sender = 4_500_000
            assertEquals(0, new BigDecimal("4500000").compareTo(reloadFromAccount.getBalance()));
            // assert receiver = 3_500_000
            assertEquals(0, new BigDecimal("3500000").compareTo(reloadToAccount.getBalance()));

            // load transfer using returned id
            TransferEntity savedTransfer = transferRepository.findById(response.id())
                    .orElseThrow(() -> new TransferNotFoundException(response.id()));
            // assert status COMPLETED
            assertEquals(TransferStatus.COMPLETED, savedTransfer.getStatus());

            // load ledger using findByTransferId(...)
            // assert exactly 2 entries
            List<LedgerEntryEntity> ledgerEntries =
                    ledgerEntryRepository.findByTransferId(savedTransfer.getId());

            assertEquals(2, ledgerEntries.size());

            LedgerEntryEntity loadedFromLedger = ledgerEntries
                    .stream()
                    .filter(leger -> leger.getEntryType().equals(LedgerEntryType.DEBIT))
                    .findFirst()
                    .orElseThrow();

            LedgerEntryEntity loadedToLedger = ledgerEntries
                    .stream()
                    .filter(ledger -> ledger.getEntryType() == LedgerEntryType.CREDIT)
                    .findFirst()
                    .orElseThrow();

            assertEquals(0, request.amount().compareTo(loadedFromLedger.getAmount()));
            assertEquals(0, request.amount().compareTo(loadedToLedger.getAmount()));

            assertEquals(request.currency(), loadedFromLedger.getCurrency());
            assertEquals(request.currency(), loadedToLedger.getCurrency());

            assertEquals(loadedFromLedger.getAccount().getId(), fromAccount.getId());
            assertEquals(loadedToLedger.getAccount().getId(), toAccount.getId());

            // calculate totalDebit
            // calculate totalCredit
            // assert debit == credit
            BigDecimal totalDebit = ledgerEntries.stream()
                    .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                    .map(LedgerEntryEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredit = ledgerEntries.stream()
                    .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                    .map(LedgerEntryEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(
                    0,
                    totalDebit.compareTo(totalCredit)
            );
        } finally {
            // Cleanup in finally
            if (transferId != null) {
                List<LedgerEntryEntity> entries =
                        ledgerEntryRepository.findByTransferId(transferId);

                ledgerEntryRepository.deleteAll(entries);

                transferRepository.deleteById(transferId);
            }

            accountRepository.deleteById(fromAccount.getId());
            accountRepository.deleteById(toAccount.getId());

            userRepository.deleteById(savedUser.getId());

        }


    }

    @Test
    void createTransfer_shouldRollbackEverything_whenSenderHasInsufficientFunds() {
        // Arrange

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity savedUser = userRepository.save(
                new UserEntity(
                        "Rollback Test User",
                        "rollback-" + suffix + "@test.com"
                )
        );

        List<AccountEntity> savedAccounts =
                accountRepository.saveAll(
                        List.of(
                                new AccountEntity(
                                        savedUser,
                                        "ROLLBACK-SENDER-" + suffix,
                                        new BigDecimal("100000"),
                                        AccountStatus.ACTIVE
                                ),
                                new AccountEntity(
                                        savedUser,
                                        "ROLLBACK-RECEIVER-" + suffix,
                                        new BigDecimal("3000000"),
                                        AccountStatus.ACTIVE
                                )
                        )
                );

        AccountEntity sender = savedAccounts.get(0);
        AccountEntity receiver = savedAccounts.get(1);

        CreateTransferRequest request =
                new CreateTransferRequest(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("500000"),
                        Currency.VND
                );

        long transferCountBefore =
                transferRepository.count();

        long ledgerCountBefore =
                ledgerEntryRepository.count();

        try {
            // Act + Assert exception

            assertThrows(
                    InsufficientFundsException.class,
                    () -> transferService.createTransfer(request)
            );

            // Assert DB state
            AccountEntity reloadedSender =
                    accountRepository.findById(sender.getId())
                            .orElseThrow();

            AccountEntity reloadedReceiver =
                    accountRepository.findById(receiver.getId())
                            .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("100000")
                            .compareTo(reloadedSender.getBalance())
            );

            assertEquals(
                    0,
                    new BigDecimal("3000000")
                            .compareTo(reloadedReceiver.getBalance())
            );

            assertEquals(
                    transferCountBefore,
                    transferRepository.count()
            );

            assertEquals(
                    ledgerCountBefore,
                    ledgerEntryRepository.count()
            );

        } finally {
            accountRepository.deleteById(sender.getId());
            accountRepository.deleteById(receiver.getId());
            userRepository.deleteById(savedUser.getId());
        }
    }
}
