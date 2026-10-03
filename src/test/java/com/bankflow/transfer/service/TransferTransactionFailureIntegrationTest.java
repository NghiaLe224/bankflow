package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.ledger.service.LedgerService;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
public class TransferTransactionFailureIntegrationTest {
    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    void createTransfer_shouldRollbackEverything_whenLedgerFails() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity user = userRepository.save(new UserEntity(
                "Late Failure Test User",
                "late-failure-" + suffix + "@test.com"
        ));

        List<AccountEntity> savedAccounts = accountRepository.saveAll(
                List.of(
                        new AccountEntity(
                                user,
                                "LATE-SENDER-" + suffix,
                                new BigDecimal("5000000"),
                                AccountStatus.ACTIVE
                        ),
                        new AccountEntity(
                                user,
                                "LATE-RECEIVER-" + suffix,
                                new BigDecimal("3000000"),
                                AccountStatus.ACTIVE
                        )
                )
        );

        AccountEntity sender = savedAccounts.get(0);
        AccountEntity receiver = savedAccounts.get(1);

        try {
            CreateTransferRequest request = new CreateTransferRequest(
                    sender.getId(),
                    receiver.getId(),
                    new BigDecimal("500000"),
                    Currency.VND
            );

            long transferCountBefore = transferRepository.count();
            long ledgerCountBefore = ledgerEntryRepository.count();

            doThrow(new RuntimeException("Simulated ledger failure"))
                    .when(ledgerService)
                    .createBalancedEntries(any(TransferEntity.class));

            assertThrows(
                    RuntimeException.class,
                    () -> transferService.createTransfer(request)
            );

            AccountEntity reloadedSender = accountRepository.findById(sender.getId())
                    .orElseThrow(() -> new AccountNotFoundException(sender.getId()));

            AccountEntity reloadedReceiver = accountRepository.findById(receiver.getId())
                    .orElseThrow(() -> new AccountNotFoundException(receiver.getId()));

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
}
