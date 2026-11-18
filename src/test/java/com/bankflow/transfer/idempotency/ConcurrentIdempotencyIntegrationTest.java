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
import com.bankflow.transfer.service.TransferRequestFingerprint;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ConcurrentIdempotencyIntegrationTest {

    @Autowired
    private IdempotencyTransferService idempotencyTransferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransferRequestFingerprint transferRequestFingerprint;

    @Test
    void concurrentSameKey_shouldReplayOneTransfer() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        List<TransferResponse> successes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

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
        CreateTransferRequest request = new CreateTransferRequest(
                accountA.getId(),
                accountB.getId(),
                new BigDecimal("1000000"),
                Currency.VND
        );

        try {
            CountDownLatch readyGate = new CountDownLatch(2);
            List<Future<TransferResponse>> futures = new ArrayList<>();

            for (int i = 0; i < 2; ++i) {
                Future<TransferResponse> future = executor.submit(() -> {
                    readyGate.countDown();
                    try {
                        if (!startGate.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start gate timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Worker interrupted");
                    }

                    try {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                                sender.getId().toString(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ));
                        SecurityContextHolder.setContext(context);
                        return idempotencyTransferService.create(request, idempotencyKey);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });
                futures.add(future);
            }

            try {
                assertTrue(readyGate.await(3, TimeUnit.SECONDS));
            } finally {
                startGate.countDown();
            }

            for (Future<TransferResponse> future : futures) {
                try {
                    TransferResponse response = future.get(10, TimeUnit.SECONDS);
                    successes.add(response);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    failures.add(cause);
                }
            }

            assertEquals(2, successes.size());
            assertEquals(0, failures.size());

            assertEquals(
                    successes.get(0).id(),
                    successes.get(1).id()
            );

            System.out.println("Success count: " + successes.size());
            System.out.println("Failure count: " + failures.size());

            failures.forEach(e ->
                    System.out.println("Failure type: " + e.getClass().getName())
            );

            List<TransferEntity> transfers = transferRepository
                    .findByFromAccount_Id(accountA.getId());

            assertEquals(1, transfers.size());
            assertEquals(2, ledgerEntryRepository.findByTransferId(transfers.get(0).getId()).size());

            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();

            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();

            assertEquals(0, new BigDecimal("4000000").compareTo(balanceA));
            assertEquals(0, new BigDecimal("2000000").compareTo(balanceB));

            IdempotencyRecordEntity record =
                    idempotencyRecordRepository
                            .findByUser_IdAndIdempotencyKey(
                                    sender.getId(),
                                    idempotencyKey
                            )
                            .orElseThrow();

            assertEquals(
                    transfers.get(0).getId(),
                    record.getTransfer().getId()
            );


        } finally {
            startGate.countDown();
            executor.shutdown();
            boolean terminated;

            try {
                terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
                if (!terminated) {
                    executor.shutdownNow();
                    terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw e;
            }

            if (!terminated) {
                throw new AssertionError("worker terminated failed");
            }

            idempotencyRecordRepository.findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey).ifPresent(idempotencyRecordRepository::delete);
            List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountA.getId());
            for (TransferEntity transfer : transfers) {
                ledgerEntryRepository.deleteAll(ledgerEntryRepository.findByTransferId(transfer.getId()));
            }
            transferRepository.deleteAll(transfers);
            accountRepository.deleteById(accountA.getId());
            accountRepository.deleteById(accountB.getId());
            userRepository.deleteById(sender.getId());
            userRepository.deleteById(receiver.getId());
        }

    }

    @Test
    void concurrentSameKeyDifferentPayload_shouldConflict() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<TransferResponse>> futures = new ArrayList<>();
        List<TransferResponse> successes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String idempotencyKey = "concurrent-conflict-" + suffix;

        UserEntity sender = userRepository.save(
                new UserEntity(
                        "Concurrent Sender",
                        "concurrent-sender-" + suffix + "@test.com"
                )
        );

        UserEntity receiver = userRepository.save(
                new UserEntity(
                        "Concurrent Receiver",
                        "concurrent-receiver-" + suffix + "@test.com"
                )
        );

        AccountEntity accountA = accountRepository.save(
                new AccountEntity(
                        sender,
                        "CONFLICT-A-" + suffix,
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                )
        );

        AccountEntity accountB = accountRepository.save(
                new AccountEntity(
                        receiver,
                        "CONFLICT-B-" + suffix,
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                )
        );

        CreateTransferRequest requestA = new CreateTransferRequest(
                accountA.getId(),
                accountB.getId(),
                new BigDecimal("1000000"),
                Currency.VND
        );

        CreateTransferRequest requestB = new CreateTransferRequest(
                accountA.getId(),
                accountB.getId(),
                new BigDecimal("2000000"),
                Currency.VND
        );

        try {

            List<CreateTransferRequest> requests =
                    List.of(requestA, requestB);

            for (CreateTransferRequest request : requests) {

                Future<TransferResponse> future = executor.submit(() -> {

                    readyGate.countDown();

                    try {
                        if (!startGate.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Start gate timeout"
                            );
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }

                    try {
                        SecurityContext context =
                                SecurityContextHolder.createEmptyContext();

                        context.setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        sender.getId().toString(),
                                        null,
                                        List.of(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_USER"
                                                )
                                        )
                                )
                        );

                        SecurityContextHolder.setContext(context);

                        return idempotencyTransferService.create(
                                request,
                                idempotencyKey
                        );

                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });

                futures.add(future);
            }

            assertTrue(
                    readyGate.await(5, TimeUnit.SECONDS),
                    "Both workers must be ready"
            );

            startGate.countDown();

            for (Future<TransferResponse> future : futures) {

                try {
                    TransferResponse response =
                            future.get(10, TimeUnit.SECONDS);

                    successes.add(response);

                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }

            assertEquals(1, successes.size());

            assertEquals(1, failures.size());

            assertInstanceOf(
                    IdempotencyConflictException.class,
                    failures.get(0)
            );

            List<TransferEntity> transfers =
                    transferRepository.findByFromAccount_Id(accountA.getId());

            assertEquals(1, transfers.size());

            TransferEntity committedTransfer = transfers.get(0);

            assertEquals(
                    2,
                    ledgerEntryRepository
                            .findByTransferId(committedTransfer.getId())
                            .size()
            );

            IdempotencyRecordEntity record =
                    idempotencyRecordRepository
                            .findByUser_IdAndIdempotencyKey(
                                    sender.getId(),
                                    idempotencyKey
                            )
                            .orElseThrow();

            assertEquals(
                    committedTransfer.getId(),
                    record.getTransfer().getId()
            );

            assertEquals(
                    committedTransfer.getId(),
                    successes.get(0).id()
            );

            String fingerprintA =
                    transferRequestFingerprint.fingerprint(requestA);

            String fingerprintB =
                    transferRequestFingerprint.fingerprint(requestB);

            String committedFingerprint =
                    record.getRequestFingerprint();

            assertTrue(
                    committedFingerprint.equals(fingerprintA)
                            || committedFingerprint.equals(fingerprintB)
            );

            BigDecimal transferredAmount =
                    committedFingerprint.equals(fingerprintA)
                            ? new BigDecimal("1000000")
                            : new BigDecimal("2000000");

            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();

            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();

            BigDecimal expectedBalanceA =
                    new BigDecimal("5000000").subtract(transferredAmount);

            BigDecimal expectedBalanceB =
                    new BigDecimal("1000000").add(transferredAmount);

            assertEquals(
                    0,
                    expectedBalanceA.compareTo(balanceA)
            );

            assertEquals(
                    0,
                    expectedBalanceB.compareTo(balanceB)
            );

        } finally {

            startGate.countDown();

            executor.shutdown();

            boolean terminated;

            try {
                terminated = executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                );

                if (!terminated) {
                    executor.shutdownNow();
                    terminated = executor.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    );
                }

            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw e;
            }

            if (!terminated) {
                throw new AssertionError(
                        "Worker threads did not terminate"
                );
            }

            // Cleanup in foreign-key dependency order.
            idempotencyRecordRepository
                    .findByUser_IdAndIdempotencyKey(
                            sender.getId(),
                            idempotencyKey
                    )
                    .ifPresent(idempotencyRecordRepository::delete);

            List<TransferEntity> transfersToDelete =
                    transferRepository.findByFromAccount_Id(accountA.getId());

            for (TransferEntity transfer : transfersToDelete) {
                ledgerEntryRepository.deleteAll(
                        ledgerEntryRepository.findByTransferId(
                                transfer.getId()
                        )
                );
            }

            transferRepository.deleteAll(transfersToDelete);

            accountRepository.deleteById(accountA.getId());
            accountRepository.deleteById(accountB.getId());

            userRepository.deleteById(sender.getId());
            userRepository.deleteById(receiver.getId());
        }
    }
}
