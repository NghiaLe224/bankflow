
package com.bankflow.transfer.idempotency;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.IdempotencyRecordEntity;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.repository.IdempotencyRecordRepository;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.transfer.service.IdempotencyRaceHook;
import com.bankflow.transfer.service.IdempotencyRecoveryService;
import com.bankflow.transfer.service.IdempotencyTransferService;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(DeterministicIdempotencyIntegrationTest.RaceTestConfig.class)
class DeterministicIdempotencyIntegrationTest {

    @Autowired
    private RaceControl raceControl;

    @Autowired
    private IdempotencyTransferService idempotencyTransferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @MockitoSpyBean
    private IdempotencyRecoveryService recoveryService;

    @TestConfiguration
    static class RaceTestConfig {

        @Bean
        RaceControl raceControl() {
            return new RaceControl();
        }

        @Bean
        @Primary
        IdempotencyRaceHook deterministicRaceHook(
                RaceControl control
        ) {
            return control::afterRecordNotFound;
        }
    }

    static class RaceControl {

        private final CountDownLatch bothSelectedEmpty =
                new CountDownLatch(2);

        private final CountDownLatch releaseBoth =
                new CountDownLatch(1);

        void afterRecordNotFound() {
            bothSelectedEmpty.countDown();

            try {
                if (!releaseBoth.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Race synchronization timeout"
                    );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "Race synchronization interrupted",
                        e
                );
            }
        }

        boolean awaitBothSelectedEmpty()
                throws InterruptedException {
            return bothSelectedEmpty.await(
                    10,
                    TimeUnit.SECONDS
            );
        }

        void release() {
            releaseBoth.countDown();
        }

        long remainingWorkers() {
            return bothSelectedEmpty.getCount();
        }
    }

    @Test
    void concurrentSameKey_afterBothSelectEmpty_shouldRecover()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        String suffix =
                UUID.randomUUID().toString().substring(0, 8);

        String idempotencyKey =
                "deterministic-" + suffix;

        UserEntity sender = userRepository.save(
                new UserEntity(
                        "Deterministic Sender",
                        "det-sender-" + suffix + "@test.com"
                )
        );

        UserEntity receiver = userRepository.save(
                new UserEntity(
                        "Deterministic Receiver",
                        "det-receiver-" + suffix + "@test.com"
                )
        );

        AccountEntity accountA = accountRepository.save(
                new AccountEntity(
                        sender,
                        "DET-A-" + suffix,
                        new BigDecimal("5000000"),
                        AccountStatus.ACTIVE
                )
        );

        AccountEntity accountB = accountRepository.save(
                new AccountEntity(
                        receiver,
                        "DET-B-" + suffix,
                        new BigDecimal("1000000"),
                        AccountStatus.ACTIVE
                )
        );

        CreateTransferRequest request =
                new CreateTransferRequest(
                        accountA.getId(),
                        accountB.getId(),
                        new BigDecimal("1000000"),
                        Currency.VND
                );

        try {
            Future<TransferResponse> futureA =
                    executor.submit(() ->
                            executeAsUser(
                                    sender.getId(),
                                    request,
                                    idempotencyKey
                            )
                    );

            Future<TransferResponse> futureB =
                    executor.submit(() ->
                            executeAsUser(
                                    sender.getId(),
                                    request,
                                    idempotencyKey
                            )
                    );

            // Both transactions must have SELECTed empty.
            assertTrue(
                    raceControl.awaitBothSelectedEmpty(),
                    "Both workers must reach SELECT empty"
            );

            assertEquals(
                    0,
                    raceControl.remainingWorkers()
            );

            // Allow both transactions to continue.
            raceControl.release();

            TransferResponse responseA =
                    futureA.get(15, TimeUnit.SECONDS);

            TransferResponse responseB =
                    futureB.get(15, TimeUnit.SECONDS);

            // Both responses must refer to the same transfer.
            assertEquals(
                    responseA.id(),
                    responseB.id()
            );

            // Exactly one committed transfer.
            List<TransferEntity> transfers =
                    transferRepository.findByFromAccount_Id(
                            accountA.getId()
                    );

            assertEquals(1, transfers.size());

            TransferEntity committedTransfer =
                    transfers.get(0);

            assertEquals(
                    committedTransfer.getId(),
                    responseA.id()
            );

            // One transfer = two ledger entries.
            assertEquals(
                    2,
                    ledgerEntryRepository
                            .findByTransferId(
                                    committedTransfer.getId()
                            )
                            .size()
            );

            // Check persisted idempotency record.
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

            // Verify balance was updated only once.
            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();

            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();

            assertEquals(
                    0,
                    new BigDecimal("4000000")
                            .compareTo(balanceA)
            );

            assertEquals(
                    0,
                    new BigDecimal("2000000")
                            .compareTo(balanceB)
            );

            // Prove recovery was actually executed.
            verify(
                    recoveryService,
                    times(1)
            ).recover(
                    any(CreateTransferRequest.class),
                    eq(idempotencyKey)
            );

        } finally {

            // Always release waiting workers on failures.
            raceControl.release();

            executor.shutdown();

            boolean terminated;

            try {
                terminated = executor.awaitTermination(
                        15,
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
                    .ifPresent(
                            idempotencyRecordRepository::delete
                    );

            List<TransferEntity> transfersToDelete =
                    transferRepository.findByFromAccount_Id(
                            accountA.getId()
                    );

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

    private TransferResponse executeAsUser(
            Long userId,
            CreateTransferRequest request,
            String idempotencyKey
    ) {
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_USER"
                                )
                        )
                )
        );

        try {
            SecurityContextHolder.setContext(context);

            return idempotencyTransferService.create(
                    request,
                    idempotencyKey
            );

        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
