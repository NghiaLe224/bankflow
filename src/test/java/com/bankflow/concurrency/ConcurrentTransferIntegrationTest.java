package com.bankflow.concurrency;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.transfer.service.TransferService;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ConcurrentTransferIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    @Transactional
    void twoSequentialTransfers_shouldRejectSecondTransferWhenBalanceIsInsufficient() {
        try {
            UserEntity savedUser = userRepository
                    .save(
                            new UserEntity("username", "user@example.com")
                    );

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(
                            savedUser.getId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            AccountEntity accountA = new AccountEntity(
                    savedUser,
                    "ACC0001",
                    new BigDecimal("150000"),
                    AccountStatus.ACTIVE
            );
            AccountEntity accountB = new AccountEntity(
                    savedUser,
                    "ACC0002",
                    new BigDecimal("0"),
                    AccountStatus.ACTIVE
            );
            AccountEntity accountC = new AccountEntity(
                    savedUser,
                    "ACC0003",
                    new BigDecimal("0"),
                    AccountStatus.ACTIVE
            );
            accountRepository.saveAll(List.of(accountA, accountB, accountC));

            CreateTransferRequest succeedRequest = new CreateTransferRequest(
                    accountA.getId(),
                    accountB.getId(),
                    new BigDecimal("100000"),
                    Currency.VND
            );
            CreateTransferRequest insufficientRequest = new CreateTransferRequest(
                    accountA.getId(),
                    accountC.getId(),
                    new BigDecimal("100000"),
                    Currency.VND
            );

            TransferResponse succeedResponse = transferService.createTransfer(succeedRequest);
            assertThrows(
                    InsufficientFundsException.class,
                    () -> transferService.createTransfer(insufficientRequest)
            );

            AccountEntity actualA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow();
            AccountEntity actualB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow();
            AccountEntity actualC = accountRepository
                    .findById(accountC.getId())
                    .orElseThrow();

            assertEquals(TransferStatus.COMPLETED, succeedResponse.status());

            assertEquals(0, new BigDecimal("50000").compareTo(actualA.getBalance()));
            assertEquals(0, new BigDecimal("100000").compareTo(actualB.getBalance()));
            assertEquals(0, BigDecimal.ZERO.compareTo(actualC.getBalance()));
        } finally {
            SecurityContextHolder.clearContext();
        }

    }

    @Test
    void twoWorkers_shouldHaveIndependentSecurityContexts()
            throws InterruptedException {

        AtomicReference<String> nameA = new AtomicReference<>();
        AtomicReference<String> nameB = new AtomicReference<>();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch bothReady = new CountDownLatch(2);

        Runnable taskA = () -> {
            try {
                Authentication authA =
                        new UsernamePasswordAuthenticationToken(
                                "101",
                                null,
                                List.of(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )
                        );
                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authA);
                SecurityContextHolder.setContext(context);

                bothReady.countDown();
                startGate.await();

                nameA.set(SecurityContextHolder.getContext().getAuthentication().getName());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        Runnable taskB = () -> {
            try {
                Authentication authB = new UsernamePasswordAuthenticationToken(
                        "202",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authB);
                SecurityContextHolder.setContext(context);

                bothReady.countDown();
                startGate.await();

                nameB.set(SecurityContextHolder.getContext().getAuthentication().getName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        Thread threadA = new Thread(taskA);
        Thread threadB = new Thread(taskB);
        threadA.start();
        threadB.start();

        boolean ready;
        try {
            ready = bothReady.await(3, TimeUnit.SECONDS);
        } finally {
            startGate.countDown();
        }

        threadA.join(3000);
        threadB.join(3000);

        assertTrue(ready);

        assertFalse(threadA.isAlive());
        assertFalse(threadB.isAlive());

        assertEquals("101", nameA.get());
        assertEquals("202", nameB.get());
    }

    @Test
    void committedFixture_shouldBeVisibleOutsideSetupTransaction()
            throws InterruptedException {

        Long userId = null;
        Long accountId = null;

        try {
            String suffix = UUID.randomUUID()
                    .toString()
                    .substring(0, 8);

            UserEntity user = new UserEntity(
                    "username",
                    "user" + suffix + "@example.com"
            );

            UserEntity savedUser = userRepository.save(user);
            userId = savedUser.getId();

            AccountEntity account = new AccountEntity(
                    savedUser,
                    "ACC" + suffix,
                    new BigDecimal("150000"),
                    AccountStatus.ACTIVE
            );

            AccountEntity savedAccount =
                    accountRepository.save(account);

            accountId = savedAccount.getId();

            Long committedAccountId = savedAccount.getId();

            AtomicReference<BigDecimal> balanceA =
                    new AtomicReference<>();

            AtomicReference<BigDecimal> balanceB =
                    new AtomicReference<>();

            AtomicReference<Throwable> workerError =
                    new AtomicReference<>();

            CountDownLatch startGate =
                    new CountDownLatch(1);

            Thread threadA = new Thread(() -> {
                try {
                    startGate.await();

                    AccountEntity actualAccount =
                            accountRepository.findById(committedAccountId)
                                    .orElseThrow();

                    balanceA.set(actualAccount.getBalance());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    workerError.compareAndSet(null, e);
                } catch (Throwable e) {
                    workerError.compareAndSet(null, e);
                }
            }, "reader-A");

            Thread threadB = new Thread(() -> {
                try {
                    startGate.await();

                    AccountEntity actualAccount =
                            accountRepository.findById(committedAccountId)
                                    .orElseThrow();

                    balanceB.set(actualAccount.getBalance());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    workerError.compareAndSet(null, e);
                } catch (Throwable e) {
                    workerError.compareAndSet(null, e);
                }
            }, "reader-B");

            threadA.start();
            threadB.start();

            startGate.countDown();

            threadA.join(3000);
            threadB.join(3000);

            assertFalse(threadA.isAlive(), "Thread A is still alive");
            assertFalse(threadB.isAlive(), "Thread B is still alive");

            assertNull(workerError.get(), "Worker failed");

            assertNotNull(balanceA.get());
            assertNotNull(balanceB.get());

            assertEquals(
                    0,
                    new BigDecimal("150000").compareTo(balanceA.get())
            );

            assertEquals(
                    0,
                    new BigDecimal("150000").compareTo(balanceB.get())
            );

        } finally {
            if (accountId != null) {
                accountRepository.deleteById(accountId);
            }

            if (userId != null) {
                userRepository.deleteById(userId);
            }
        }
    }


    @Test
    void twoConcurrentTransfers_shouldExposeRaceCondition()
            throws InterruptedException {

        Long userId = null;
        Long sourceAccountId = null;

        List<Long> accountIds = new ArrayList<>();

        Thread threadA = null;
        Thread threadB = null;

        CountDownLatch startGate = new CountDownLatch(1);

        try {
            String suffix = UUID.randomUUID()
                    .toString()
                    .substring(0, 8);

            UserEntity savedUser = userRepository.save(
                    new UserEntity(
                            "username",
                            "user" + suffix + "@example.com"
                    )
            );
            userId = savedUser.getId();

            AccountEntity accountA = new AccountEntity(
                    savedUser,
                    "ACCA" + suffix,
                    new BigDecimal("150000"),
                    AccountStatus.ACTIVE
            );
            AccountEntity accountB = new AccountEntity(
                    savedUser,
                    "ACCB" + suffix,
                    BigDecimal.ZERO,
                    AccountStatus.ACTIVE
            );
            AccountEntity accountC = new AccountEntity(
                    savedUser,
                    "ACCC" + suffix,
                    BigDecimal.ZERO,
                    AccountStatus.ACTIVE
            );

            List<AccountEntity> accounts = accountRepository.saveAll(
                    List.of(accountA, accountB, accountC)
            );

            for (AccountEntity account : accounts) {
                accountIds.add(account.getId());
            }

            sourceAccountId = accountA.getId();

            CreateTransferRequest requestA =
                    new CreateTransferRequest(
                            accountA.getId(),
                            accountB.getId(),
                            new BigDecimal("100000"),
                            Currency.VND
                    );
            CreateTransferRequest requestB =
                    new CreateTransferRequest(
                            accountA.getId(),
                            accountC.getId(),
                            new BigDecimal("100000"),
                            Currency.VND
                    );

            AtomicReference<TransferResponse> responseA =
                    new AtomicReference<>();

            AtomicReference<TransferResponse> responseB =
                    new AtomicReference<>();

            AtomicReference<Throwable> errorA =
                    new AtomicReference<>();

            AtomicReference<Throwable> errorB =
                    new AtomicReference<>();

            CountDownLatch bothReady = new CountDownLatch(2);
            String principal = savedUser.getId().toString();

            Runnable taskA = () -> {
                try {
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority("ROLE_USER")
                                    )
                            );

                    SecurityContext context =
                            SecurityContextHolder.createEmptyContext();

                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);

                    bothReady.countDown();
                    startGate.await();

                    TransferResponse response =
                            transferService.createTransfer(requestA);

                    responseA.set(response);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorA.set(e);

                } catch (Throwable e) {
                    errorA.set(e);

                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Runnable taskB = () -> {
                try {
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority("ROLE_USER")
                                    )
                            );

                    SecurityContext context =
                            SecurityContextHolder.createEmptyContext();

                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);

                    bothReady.countDown();
                    startGate.await();

                    TransferResponse response =
                            transferService.createTransfer(requestB);

                    responseB.set(response);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorB.set(e);

                } catch (Throwable e) {
                    errorB.set(e);

                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            threadA = new Thread(taskA, "transfer-A");
            threadB = new Thread(taskB, "transfer-B");

            threadA.start();
            threadB.start();

            boolean ready;

            try {
                ready = bothReady.await(3, TimeUnit.SECONDS);
            } finally {
                startGate.countDown();
            }

            threadA.join(3000);
            threadB.join(3000);

            assertFalse(threadA.isAlive(), "Thread A is still running");
            assertFalse(threadB.isAlive(), "Thread B is still running");
            assertTrue(ready, "Workers were not ready");

            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal balanceC = accountRepository
                    .findById(accountC.getId())
                    .orElseThrow()
                    .getBalance();

            boolean successA = responseA.get() != null;
            boolean successB = responseB.get() != null;
            int successCount =
                    (successA ? 1 : 0) + (successB ? 1 : 0);

            BigDecimal totalBalance =
                    balanceA.add(balanceB).add(balanceC);

            String diagnostic = """
                    Concurrent Transfer Result:
                    successCount = %d
                    balanceA = %s
                    balanceB = %s
                    balanceC = %s
                    totalBalance = %s
                    errorA = %s
                    errorB = %s
                    """.formatted(
                    successCount,
                    balanceA,
                    balanceB,
                    balanceC,
                    totalBalance,
                    errorA.get(),
                    errorB.get()
            );

            assertEquals(1, successCount, diagnostic);

            TransferResponse successfulResponse =
                    successA ? responseA.get() : responseB.get();
            Throwable failedTransferError =
                    successA ? errorB.get() : errorA.get();
            Throwable successfulTransferError =
                    successA ? errorA.get() : errorB.get();

            assertEquals(
                    TransferStatus.COMPLETED,
                    successfulResponse.status()
            );

            assertNull(successfulTransferError);

            assertInstanceOf(
                    InsufficientFundsException.class,
                    failedTransferError
            );

            assertEquals(
                    0,
                    new BigDecimal("50000").compareTo(balanceA)
            );

            if (successA) {
                assertEquals(
                        0,
                        new BigDecimal("100000").compareTo(balanceB)
                );

                assertEquals(
                        0,
                        BigDecimal.ZERO.compareTo(balanceC)
                );
            } else {
                assertEquals(
                        0,
                        BigDecimal.ZERO.compareTo(balanceB)
                );

                assertEquals(
                        0,
                        new BigDecimal("100000").compareTo(balanceC)
                );
            }

        } finally {
            startGate.countDown();

            boolean workerStillRunning =
                    (threadA != null && threadA.isAlive()) ||
                            (threadB != null && threadB.isAlive());

            if (workerStillRunning) {
                System.err.println(
                        "Skipping cleanup: a transfer worker is still running. " +
                                "Inspect test fixture manually. User ID: " + userId
                );
            } else {
                if (sourceAccountId != null) {
                    List<TransferEntity> transfers =
                            transferRepository.findByFromAccount_Id(
                                    sourceAccountId
                            );

                    for (TransferEntity transfer : transfers) {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransferId(
                                        transfer.getId()
                                )
                        );
                    }

                    transferRepository.deleteAll(transfers);
                }

                for (Long id : accountIds) {
                    accountRepository.deleteById(id);
                }

                if (userId != null) {
                    userRepository.deleteById(userId);
                }
            }
        }
    }

    @Test
    void twoConcurrentCredits_shouldPreserveDestinationBalance()
            throws InterruptedException {

        List<Long> accountIds = new ArrayList<>();
        Long firstSourceAccountId = null;
        Long secondSourceAccountId = null;
        Long userId = null;
        CountDownLatch startGate = new CountDownLatch(1);
        Thread threadAC = null;
        Thread threadBC = null;

        try {
            String suffix = UUID.randomUUID()
                    .toString()
                    .substring(0, 8);

            UserEntity savedUser = userRepository.save(
                    new UserEntity(
                            "username",
                            "user" + suffix + "@example.com"
                    )
            );
            userId = savedUser.getId();

            AccountEntity accountA = new AccountEntity(
                    savedUser,
                    "ACCA" + suffix,
                    new BigDecimal("200000"),
                    AccountStatus.ACTIVE
            );
            AccountEntity accountB = new AccountEntity(
                    savedUser,
                    "ACCB" + suffix,
                    new BigDecimal("200000"),
                    AccountStatus.ACTIVE
            );
            AccountEntity accountC = new AccountEntity(
                    savedUser,
                    "ACCC" + suffix,
                    BigDecimal.ZERO,
                    AccountStatus.ACTIVE
            );

            List<AccountEntity> accounts = accountRepository.saveAll(
                    List.of(accountA, accountB, accountC)
            );

            for (AccountEntity account : accounts) {
                accountIds.add(account.getId());
            }

            firstSourceAccountId = accountA.getId();
            secondSourceAccountId = accountB.getId();

            CreateTransferRequest requestAC =
                    new CreateTransferRequest(
                            accountA.getId(),
                            accountC.getId(),
                            new BigDecimal("100000"),
                            Currency.VND
                    );
            CreateTransferRequest requestBC =
                    new CreateTransferRequest(
                            accountB.getId(),
                            accountC.getId(),
                            new BigDecimal("100000"),
                            Currency.VND
                    );

            AccountEntity actualA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow();
            AccountEntity actualB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow();
            AccountEntity actualC = accountRepository
                    .findById(accountC.getId())
                    .orElseThrow();


            assertNotNull(savedUser.getId());

            assertNotNull(accountA.getId());
            assertNotNull(accountB.getId());
            assertNotNull(accountC.getId());

            assertEquals(3, accountIds.size());

            assertNotEquals(
                    requestAC.fromAccountId(),
                    requestBC.fromAccountId()
            );

            assertEquals(
                    requestAC.toAccountId(),
                    requestBC.toAccountId()
            );

            assertEquals(
                    0,
                    new BigDecimal("200000")
                            .compareTo(actualA.getBalance())
            );
            assertEquals(
                    0,
                    new BigDecimal("200000")
                            .compareTo(actualB.getBalance())
            );
            assertEquals(
                    0,
                    BigDecimal.ZERO
                            .compareTo(actualC.getBalance())
            );

            AtomicReference<TransferResponse> responseAC = new AtomicReference<>();
            AtomicReference<TransferResponse> responseBC = new AtomicReference<>();
            AtomicReference<Throwable> errorAC = new AtomicReference<>();
            AtomicReference<Throwable> errorBC = new AtomicReference<>();
            CountDownLatch bothReady = new CountDownLatch(2);

            Runnable taskAC = () -> {
                try {
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            savedUser.getId().toString(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority("ROLE_USER")
                            )
                    );
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);

                    bothReady.countDown();
                    startGate.await();

                    TransferResponse transferResponse =
                            transferService.createTransfer(requestAC);

                    responseAC.set(transferResponse);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorAC.set(e);
                } catch (Throwable e) {
                    errorAC.set(e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Runnable taskBC = () -> {
                try {
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            savedUser.getId().toString(),
                            null,
                            List.of(
                                    new SimpleGrantedAuthority("ROLE_USER")
                            )
                    );
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);

                    bothReady.countDown();
                    startGate.await();

                    TransferResponse transferResponse =
                            transferService.createTransfer(requestBC);

                    responseBC.set(transferResponse);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorBC.set(e);
                } catch (Throwable e) {
                    errorBC.set(e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            threadAC = new Thread(taskAC);
            threadBC = new Thread(taskBC);

            threadAC.start();
            threadBC.start();

            boolean ready;
            try {
                ready = bothReady.await(3, TimeUnit.SECONDS);
            } finally {
                startGate.countDown();
            }

            threadAC.join(3000);
            threadBC.join(3000);

            assertFalse(threadAC.isAlive());
            assertFalse(threadBC.isAlive());
            assertTrue(ready);

            assertNull(errorAC.get(), "Transfer AC failed");
            assertNull(errorBC.get(), "Transfer BC failed");

            assertNotNull(responseAC.get());
            assertNotNull(responseBC.get());

            assertEquals(
                    TransferStatus.COMPLETED,
                    responseAC.get().status()
            );

            assertEquals(
                    TransferStatus.COMPLETED,
                    responseBC.get().status()
            );

            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal balanceC = accountRepository
                    .findById(accountC.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal totalBalance = balanceA.add(balanceB).add(balanceC);

            assertEquals(0, new BigDecimal("400000")
                    .compareTo(totalBalance));
            assertEquals(0, new BigDecimal("100000")
                    .compareTo(balanceA));
            assertEquals(0, new BigDecimal("100000")
                    .compareTo(balanceB));
            assertEquals(0, new BigDecimal("200000")
                    .compareTo(balanceC));

            boolean successfulAC = responseAC.get() != null;
            boolean successfulBC = responseBC.get() != null;
            int totalSuccess = (successfulAC ? 1 : 0) + (successfulBC ? 1 : 0);

            assertEquals(2, totalSuccess);

        } finally {
            startGate.countDown();

            boolean workerStillRunning =
                    (threadAC != null && threadAC.isAlive())
                            || (threadBC != null && threadBC.isAlive());

            if (workerStillRunning) {
                System.out.println("Skipping cleanup because a worker is still running. "
                        + "User ID: " + userId);
            } else {


                if (firstSourceAccountId != null) {
                    List<TransferEntity> transfers = transferRepository
                            .findByFromAccount_Id(firstSourceAccountId);

                    for (TransferEntity transfer : transfers) {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransferId(transfer.getId())
                        );
                    }

                    transferRepository.deleteAll(transfers);
                }

                if (secondSourceAccountId != null) {
                    List<TransferEntity> transfers = transferRepository
                            .findByFromAccount_Id(secondSourceAccountId);

                    for (TransferEntity transfer : transfers) {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransferId(transfer.getId())
                        );
                    }

                    transferRepository.deleteAll(transfers);
                }

                for (Long accountId : accountIds) {
                    accountRepository.deleteById(accountId);
                }

                if (userId != null) {
                    userRepository.deleteById(userId);
                }
            }
        }
    }

    @Test
    void twoOppositeTransfers_shouldCompleteWithoutDeadlock() throws InterruptedException {

        Long userId = null;
        List<Long> accountIds = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);
        Thread threadAB = null;
        Thread threadBA = null;

        try {
            String suffix = UUID.randomUUID().toString().substring(0, 7);

            UserEntity savedUser = userRepository.save(new UserEntity(
                    "username",
                    "user" + suffix + "@example.com"
            ));
            userId = savedUser.getId();

            AccountEntity accountA = new AccountEntity(
                    savedUser,
                    "ACC01" + suffix,
                    new BigDecimal("200000"),
                    AccountStatus.ACTIVE
            );
            AccountEntity accountB = new AccountEntity(
                    savedUser,
                    "ACC02" + suffix,
                    new BigDecimal("150000"),
                    AccountStatus.ACTIVE
            );

            List<AccountEntity> accounts = accountRepository
                    .saveAll(List.of(accountA, accountB));

            for (AccountEntity account : accounts) {
                accountIds.add(account.getId());
            }

            CountDownLatch bothReady = new CountDownLatch(2);
            AtomicReference<TransferResponse> transferResponseAB = new AtomicReference<>();
            AtomicReference<TransferResponse> transferResponseBA = new AtomicReference<>();
            AtomicReference<Throwable> errorAB = new AtomicReference<>();
            AtomicReference<Throwable> errorBA = new AtomicReference<>();

            CreateTransferRequest requestAB
                    = new CreateTransferRequest(
                    accountA.getId(),
                    accountB.getId(),
                    new BigDecimal("70000"),
                    Currency.VND
            );
            CreateTransferRequest requestBA
                    = new CreateTransferRequest(
                    accountB.getId(),
                    accountA.getId(),
                    new BigDecimal("30000"),
                    Currency.VND
            );

            Runnable taskAB = () -> {
                try {
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    savedUser.getId().toString(),
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority("ROLE_USER")
                                    )
                            );
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                    bothReady.countDown();
                    startGate.await();

                    TransferResponse transferResponse
                            = transferService.createTransfer(requestAB);
                    transferResponseAB.set(transferResponse);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorAB.set(e);
                } catch (Throwable e) {
                    errorAB.set(e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            Runnable taskBA = () -> {
                try {
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    savedUser.getId().toString(),
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority("ROLE_USER")
                                    )
                            );
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                    bothReady.countDown();
                    startGate.await();

                    TransferResponse transferResponse
                            = transferService.createTransfer(requestBA);
                    transferResponseBA.set(transferResponse);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errorBA.set(e);
                } catch (Throwable e) {
                    errorBA.set(e);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            threadAB = new Thread(taskAB);
            threadBA = new Thread(taskBA);

            threadBA.start();
            threadAB.start();

            boolean ready;
            try {
                ready = bothReady.await(3, TimeUnit.SECONDS);
            } finally {
                startGate.countDown();
            }

            threadAB.join(3000);
            threadBA.join(3000);

            assertFalse(threadAB.isAlive(), "Thread AB is still running");
            assertFalse(threadBA.isAlive(), "Thread BA is still running");
            assertTrue(ready, "Workers were not ready");

            assertNull(errorAB.get(), "Transfer AB failed");
            assertNull(errorBA.get(), "Transfer BA failed");

            assertNotNull(transferResponseAB.get());
            assertNotNull(transferResponseBA.get());

            assertEquals(
                    TransferStatus.COMPLETED,
                    transferResponseAB.get().status()
            );

            assertEquals(
                    TransferStatus.COMPLETED,
                    transferResponseBA.get().status()
            );

            BigDecimal balanceA = accountRepository
                    .findById(accountA.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal balanceB = accountRepository
                    .findById(accountB.getId())
                    .orElseThrow()
                    .getBalance();
            BigDecimal totalBalance = balanceA.add(balanceB);

            assertNull(errorAB.get());
            assertNull(errorBA.get());

            boolean successA = transferResponseAB.get() != null;
            boolean successB = transferResponseBA.get() != null;
            int totalSuccess = (successA ? 1 : 0) + (successB ? 1 : 0);

            assertEquals(2, totalSuccess);

            assertEquals(0, new BigDecimal("350000")
                    .compareTo(totalBalance));
            assertEquals(0, new BigDecimal("160000")
                    .compareTo(balanceA));
            assertEquals(0, new BigDecimal("190000")
                    .compareTo(balanceB));

        } finally {
            startGate.countDown();

            boolean workerStillRunning =
                    (threadAB != null && threadAB.isAlive())
                            || (threadBA != null && threadBA.isAlive());
            if (workerStillRunning) {
                System.err.println("Skipping cleanup because a worker is still running. "
                        + "User ID: " + userId);
            } else {
                for (Long accountId : accountIds) {
                    List<TransferEntity> transfers = transferRepository.findByFromAccount_Id(accountId);

                    for (TransferEntity transfer : transfers) {
                        ledgerEntryRepository.deleteAll(
                                ledgerEntryRepository.findByTransferId(transfer.getId())
                        );
                    }

                    transferRepository.deleteAll(transfers);
                }

                for (Long accountId : accountIds) {
                    accountRepository.deleteById(accountId);
                }

                if (userId != null) {
                    userRepository.deleteById(userId);
                }
            }

        }
    }

}
