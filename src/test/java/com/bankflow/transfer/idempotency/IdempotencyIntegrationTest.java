package com.bankflow.transfer.idempotency;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.IdempotencyConflictException;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.IdempotencyRecordEntity;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.repository.IdempotencyRecordRepository;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.transfer.service.IdempotencyTransferService;
import com.bankflow.transfer.service.TransferService;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class IdempotencyIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyTransferService idempotencyTransferService;
    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void duplicateRequest_withoutIdempotencyKey_shouldCreateTwoTransfers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity sender = userRepository.save(
                new UserEntity("Idempotency Sender", "idempotency-sender-" + suffix + "@test.com")
        );
        UserEntity receiver = userRepository.save(
                new UserEntity("Idempotency Receiver", "idempotency-receiver-" + suffix + "@test.com")
        );

        AccountEntity accountA = accountRepository.save(
                new AccountEntity(
                        sender,
                        "IDEMP-SENDER-" + suffix,
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                )
        );
        AccountEntity accountB = accountRepository.save(
                new AccountEntity(
                        receiver,
                        "IDEMP-RECEIVER-" + suffix,
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                )
        );

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            sender.getId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    )
            );

            CreateTransferRequest request = new CreateTransferRequest(
                    accountA.getId(),
                    accountB.getId(),
                    new BigDecimal("1000000"),
                    Currency.VND
            );

            Long transferId1 = transferService.createTransfer(request).id();
            Long transferId2 = transferService.createTransfer(request).id();

            assertNotEquals(transferId1, transferId2);

            List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountA.getId());
            assertEquals(2, transfers.size());
            List<Long> transferIds = transfers.stream().map(TransferEntity::getId).toList();
            assertTrue(transferIds.containsAll(List.of(transferId1, transferId2)));

            BigDecimal balanceA = accountRepository.findById(accountA.getId()).orElseThrow().getBalance();
            BigDecimal balanceB = accountRepository.findById(accountB.getId()).orElseThrow().getBalance();

            assertEquals(0, new BigDecimal("3000000").compareTo(balanceA));
            assertEquals(0, new BigDecimal("3000000").compareTo(balanceB));
            assertEquals(0, new BigDecimal("6000000").compareTo(balanceA.add(balanceB)));
        } finally {
            SecurityContextHolder.clearContext();

            List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountA.getId());
            for (TransferEntity transfer : transfers) {
                ledgerEntryRepository.deleteAll(
                        ledgerEntryRepository.findByTransferId(transfer.getId())
                );
            }
            transferRepository.deleteAll(transfers);
            accountRepository.deleteById(accountA.getId());
            accountRepository.deleteById(accountB.getId());
            userRepository.deleteById(sender.getId());
            userRepository.deleteById(receiver.getId());
        }

    }

    @Test
    void sameKeyAndSamePayload_shouldCreateOnlyOneTransfer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String idempotencyKey = "abc123def456";

        UserEntity sender = userRepository.save(
                new UserEntity("Idempotency Sender", "idempotency-sender-" + suffix + "@test.com")
        );
        UserEntity receiver = userRepository.save(
                new UserEntity("Idempotency Receiver", "idempotency-receiver-" + suffix + "@test.com")
        );

        AccountEntity accountA = accountRepository.save(
                new AccountEntity(
                        sender,
                        "IDEMP-SENDER-" + suffix,
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                )
        );
        AccountEntity accountB = accountRepository.save(
                new AccountEntity(
                        receiver,
                        "IDEMP-RECEIVER-" + suffix,
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                )
        );

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            sender.getId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    )
            );

            CreateTransferRequest request = new CreateTransferRequest(
                    accountA.getId(),
                    accountB.getId(),
                    new BigDecimal("1000000"),
                    Currency.VND
            );

            TransferResponse firstResponse = idempotencyTransferService.create(request, idempotencyKey);
            TransferResponse secondResponse = idempotencyTransferService.create(request, idempotencyKey);

            assertEquals(firstResponse.id(), secondResponse.id());

            List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountA.getId());

            assertEquals(1, transfers.size());
            assertEquals(firstResponse.id(), transfers.get(0).getId());

            IdempotencyRecordEntity record =
                    idempotencyRecordRepository
                            .findByUser_IdAndIdempotencyKey(
                                    sender.getId(),
                                    idempotencyKey
                            )
                            .orElseThrow();

            assertEquals(firstResponse.id(), record.getTransfer().getId());
            assertEquals(idempotencyKey, record.getIdempotencyKey());

            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();

            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0, new BigDecimal("4000000").compareTo(balanceA)
            );

            assertEquals(
                    0, new BigDecimal("2000000").compareTo(balanceB)
            );

            assertEquals(
                    0,
                    new BigDecimal("6000000")
                            .compareTo(balanceA.add(balanceB))
            );

            assertEquals(
                    2,
                    ledgerEntryRepository
                            .findByTransferId(firstResponse.id())
                            .size()
            );
        } finally {
            SecurityContextHolder.clearContext();

            idempotencyRecordRepository.findByUser_IdAndIdempotencyKey(
                            sender.getId(),
                            idempotencyKey
                    )
                    .ifPresent(idempotencyRecordRepository::delete);
            List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountA.getId());
            for (TransferEntity transfer : transfers) {
                ledgerEntryRepository.deleteAll(
                        ledgerEntryRepository.findByTransferId(transfer.getId())
                );
            }
            transferRepository.deleteAll(transfers);
            accountRepository.deleteById(accountA.getId());
            accountRepository.deleteById(accountB.getId());
            userRepository.deleteById(sender.getId());
            userRepository.deleteById(receiver.getId());
        }
    }

    @Test
    void sameKeyWithDifferentPayload_shouldThrowConflict() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String idempotencyKey = "abc123def456";

        UserEntity sender = userRepository.save(
                new UserEntity("Idempotency Sender", "idempotency-sender-" + suffix + "@test.com")
        );
        UserEntity receiver = userRepository.save(
                new UserEntity("Idempotency Receiver", "idempotency-receiver-" + suffix + "@test.com")
        );

        AccountEntity accountA = accountRepository.save(
                new AccountEntity(
                        sender,
                        "IDEMP-SENDER-" + suffix,
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                )
        );
        AccountEntity accountB = accountRepository.save(
                new AccountEntity(
                        receiver,
                        "IDEMP-RECEIVER-" + suffix,
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                )
        );

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            sender.getId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    )
            );

            CreateTransferRequest firstRequest = new CreateTransferRequest(
                    accountA.getId(),
                    accountB.getId(),
                    new BigDecimal("1000000"),
                    Currency.VND
            );
            CreateTransferRequest secondRequest = new CreateTransferRequest(
                    accountA.getId(),
                    accountB.getId(),
                    new BigDecimal("2000000"),
                    Currency.VND
            );

            TransferResponse firstResponse = idempotencyTransferService.create(firstRequest, idempotencyKey);

            assertThrows(IdempotencyConflictException.class, () ->
                    idempotencyTransferService.create(secondRequest, idempotencyKey));

            List<TransferEntity> transfers =
                    transferRepository.findByFromAccount_Id(accountA.getId());

            assertEquals(1, transfers.size());
            assertEquals(firstResponse.id(), transfers.get(0).getId());

            BigDecimal balanceA = accountRepository.findById(accountA.getId())
                    .orElseThrow().getBalance();
            BigDecimal balanceB = accountRepository.findById(accountB.getId())
                    .orElseThrow().getBalance();

            assertEquals(0, balanceA.compareTo(new BigDecimal("4000000")));
            assertEquals(0, balanceB.compareTo(new BigDecimal("2000000")));
            assertEquals(0, new BigDecimal("6000000")
                    .compareTo(balanceA.add(balanceB)));

            IdempotencyRecordEntity record =
                    idempotencyRecordRepository
                            .findByUser_IdAndIdempotencyKey(
                                    sender.getId(),
                                    idempotencyKey
                            )
                            .orElseThrow();

            assertEquals(firstResponse.id(), record.getTransfer().getId());
            assertEquals(idempotencyKey, record.getIdempotencyKey());

            assertEquals(2,
                    ledgerEntryRepository
                            .findByTransferId(firstResponse.id()).size());
        } finally {
            SecurityContextHolder.clearContext();

            idempotencyRecordRepository.findByUser_IdAndIdempotencyKey(
                            sender.getId(),
                            idempotencyKey
                    )
                    .ifPresent(idempotencyRecordRepository::delete);
            List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountA.getId());
            for (TransferEntity transfer : transfers) {
                ledgerEntryRepository.deleteAll(
                        ledgerEntryRepository.findByTransferId(transfer.getId())
                );
            }
            transferRepository.deleteAll(transfers);
            accountRepository.deleteById(accountA.getId());
            accountRepository.deleteById(accountB.getId());
            userRepository.deleteById(sender.getId());
            userRepository.deleteById(receiver.getId());
        }
    }
}
