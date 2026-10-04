package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountInactiveException;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.common.exception.InvalidTransferException;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.enums.Currency;
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
class TransferInvariantIntegrationTest {

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
    void createTransfer_shouldReject_whenSenderIsInactive() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = userRepository.save(
                new UserEntity(
                        "Invariant Test User",
                        "invariant-" + suffix + "@test.com"
                )
        );

        List<AccountEntity> accounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                user,
                                "INV-SENDER-" + suffix,
                                new BigDecimal("5000000"),
                                AccountStatus.BLOCKED
                        ),
                        new AccountEntity(
                                user,
                                "INV-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.ACTIVE
                        )
                )
        );

        AccountEntity sender = accounts.get(0);
        AccountEntity receiver = accounts.get(1);

        try {

            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            CreateTransferRequest request = new CreateTransferRequest(
                    sender.getId(),
                    receiver.getId(),
                    new BigDecimal("500000"),
                    Currency.VND
            );

            assertThrows(
                    AccountInactiveException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedSender = accountRepository.findById(sender.getId())
                    .orElseThrow();

            AccountEntity reloadedReceiver = accountRepository.findById(receiver.getId())
                    .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("5000000")
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
            userRepository.deleteById(user.getId());
        }

    }

    @Test
    void createTransfer_shouldReject_whenReceiverIsInactive() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = userRepository.save(
                new UserEntity(
                        "Invariant Receiver Test User",
                        "invariant-receiver-" + suffix + "@test.com"
                )
        );

        List<AccountEntity> accounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                user,
                                "INV-SENDER-" + suffix,
                                new BigDecimal("5000000"),
                                AccountStatus.ACTIVE
                        ),
                        new AccountEntity(
                                user,
                                "INV-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.BLOCKED
                        )
                )
        );

        AccountEntity sender = accounts.get(0);
        AccountEntity receiver = accounts.get(1);

        try {
            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            CreateTransferRequest request = new CreateTransferRequest(
                    sender.getId(),
                    receiver.getId(),
                    new BigDecimal("500000"),
                    Currency.VND
            );

            assertThrows(
                    AccountInactiveException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedSender = accountRepository.findById(sender.getId())
                    .orElseThrow();

            AccountEntity reloadedReceiver = accountRepository.findById(receiver.getId())
                    .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("5000000")
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
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void createTransfer_shouldReject_whenSenderAndReceiverAreSameAccount() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = userRepository.save(
                new UserEntity(
                        "Same Account Test User",
                        "same-account-" + suffix + "@test.com"
                )
        );

        AccountEntity account = accountRepository.save(
                new AccountEntity(
                        user,
                        "INV-SAME-" + suffix,
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                )
        );

        try {
            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            CreateTransferRequest request = new CreateTransferRequest(
                    account.getId(),
                    account.getId(),
                    new BigDecimal("500000"),
                    Currency.VND
            );

            assertThrows(
                    InvalidTransferException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedAccount = accountRepository.findById(account.getId())
                    .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("5000000")
                            .compareTo(reloadedAccount.getBalance())
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
            accountRepository.deleteById(account.getId());
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void createTransfer_shouldReject_whenAmountIsZero() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = userRepository.save(
                new UserEntity(
                        "Zero Amount Test User",
                        "zero-amount-" + suffix + "@test.com"
                )
        );

        List<AccountEntity> accounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                user,
                                "INV-ZERO-SENDER-" + suffix,
                                new BigDecimal("5000000"),
                                AccountStatus.ACTIVE
                        ),
                        new AccountEntity(
                                user,
                                "INV-ZERO-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.ACTIVE
                        )
                )
        );

        AccountEntity sender = accounts.get(0);
        AccountEntity receiver = accounts.get(1);

        try {
            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            CreateTransferRequest request = new CreateTransferRequest(
                    sender.getId(),
                    receiver.getId(),
                    BigDecimal.ZERO,
                    Currency.VND
            );

            assertThrows(
                    InvalidTransferException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedSender = accountRepository.findById(sender.getId())
                    .orElseThrow();

            AccountEntity reloadedReceiver = accountRepository.findById(receiver.getId())
                    .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("5000000")
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
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void createTransfer_shouldReject_whenAmountIsNegative() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = userRepository.save(
                new UserEntity(
                        "Negative Amount Test User",
                        "negative-amount-" + suffix + "@test.com"
                )
        );

        List<AccountEntity> accounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                user,
                                "INV-NEG-SENDER-" + suffix,
                                new BigDecimal("5000000"),
                                AccountStatus.ACTIVE
                        ),
                        new AccountEntity(
                                user,
                                "INV-NEG-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.ACTIVE
                        )
                )
        );

        AccountEntity sender = accounts.get(0);
        AccountEntity receiver = accounts.get(1);

        try {
            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            CreateTransferRequest request = new CreateTransferRequest(
                    sender.getId(),
                    receiver.getId(),
                    new BigDecimal("-500000"),
                    Currency.VND
            );

            assertThrows(
                    InvalidTransferException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedSender = accountRepository.findById(sender.getId())
                    .orElseThrow();

            AccountEntity reloadedReceiver = accountRepository.findById(receiver.getId())
                    .orElseThrow();

            assertEquals(
                    0,
                    new BigDecimal("5000000")
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
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void createTransfer_shouldReject_whenSenderHasInsufficientFunds() {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        UserEntity user = userRepository.save(
                new UserEntity(
                        "Insufficient Funds Test User",
                        "insufficient-" + suffix + "@test.com"
                )
        );

        List<AccountEntity> accounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                user,
                                "INV-INS-SENDER-" + suffix,
                                new BigDecimal("100000"),
                                AccountStatus.ACTIVE
                        ),
                        new AccountEntity(
                                user,
                                "INV-INS-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.ACTIVE
                        )
                )
        );

        AccountEntity sender = accounts.get(0);
        AccountEntity receiver = accounts.get(1);

        try {
            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            CreateTransferRequest request = new CreateTransferRequest(
                    sender.getId(),
                    receiver.getId(),
                    new BigDecimal("500000"),
                    Currency.VND
            );

            assertThrows(
                    InsufficientFundsException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedSender = accountRepository.findById(sender.getId())
                    .orElseThrow();

            AccountEntity reloadedReceiver = accountRepository.findById(receiver.getId())
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
            userRepository.deleteById(user.getId());
        }
    }
}
