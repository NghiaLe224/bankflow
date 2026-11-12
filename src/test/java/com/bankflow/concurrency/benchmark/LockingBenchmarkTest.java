package com.bankflow.concurrency.benchmark;

import com.bankflow.concurrency.optimistic.OptimisticLabEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LockingBenchmarkTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldMeasureSingleTransactionLatency() {

        Long labId = 100L;
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        insert into bankflow_optimistic_lab (id, balance, version) values (?1, 1000, 1)
                                        on conflict(id) 
                                        do update set 
                                            balance = EXCLUDED.balance, 
                                            version = EXCLUDED.version
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            long start = System.nanoTime();

            template.executeWithoutResult(status -> {
                BigDecimal balance = (BigDecimal) entityManager
                        .createNativeQuery("""
                                SELECT balance
                                FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(
                        0,
                        new BigDecimal("1000").compareTo(balance));
            });

            long elapsedNanos = System.nanoTime() - start;
            double elapsedMillis = elapsedNanos / 1_000_000.0;

            System.out.println(
                    "Transaction latency: " + elapsedMillis + " ms"
            );
        } finally {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }

    @Test
    void shouldMeasureAverageTransactionLatency() {
        Long labId = 101L;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        List<Long> latencies = new ArrayList<>();

        template.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                            """
                                    insert into bankflow_optimistic_lab 
                                        (id, balance, version) 
                                    values (?1, 1000, 1)
                                    on conflict(id) 
                                    do update set 
                                        balance = EXCLUDED.balance, 
                                        version = EXCLUDED.version
                                    """
                    )
                    .setParameter(1, labId)
                    .executeUpdate();
        });

        try {
            for (int i = 0; i < 10; ++i) {
                template.executeWithoutResult(status -> {
                    BigDecimal balance = (BigDecimal) entityManager
                            .createNativeQuery("""
                                    SELECT balance
                                    FROM bankflow_optimistic_lab
                                    WHERE id = ?1
                                    """)
                            .setParameter(1, labId)
                            .getSingleResult();
                });
            }

            for (int i = 0; i < 100; ++i) {
                long start = System.nanoTime();
                template.executeWithoutResult(status -> {
                    BigDecimal balance = (BigDecimal) entityManager
                            .createNativeQuery("""
                                    SELECT balance
                                    FROM bankflow_optimistic_lab
                                    WHERE id = ?1
                                    """)
                            .setParameter(1, labId)
                            .getSingleResult();
                });
                long elapsedNanos = System.nanoTime() - start;
                latencies.add(elapsedNanos);
            }
            long minNanos = Collections.min(latencies);
            long maxNanos = Collections.max(latencies);

            double avgNanos = latencies.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElseThrow();

            double avgMillis = avgNanos / 1000000.0;
            double minMillis = minNanos / 1000000.0;
            double maxMillis = maxNanos / 1000000.0;

            System.out.println("avgMillis = " + avgMillis);
            System.out.println("minMillis = " + minMillis);
            System.out.println("maxMillis = " + maxMillis);

        } finally {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }

    @Test
    void shouldExecuteFourWorkers() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; ++i) {
                Future<Integer> result = executor.submit(() -> 1);
                results.add(result);
            }

            int sum = 0;

            for (Future<Integer> future : results) {
                sum += future.get();
            }
            assertEquals(4, sum);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void fourWorkers_shouldUpdateSameRowSafely() throws Exception {

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startGate = new CountDownLatch(1);
        Long labId = 102L;

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        insert into bankflow_optimistic_lab (id, balance, version) 
                                        values (?1, 1000, 1) 
                                        on conflict(id) 
                                        do update set 
                                           balance = EXCLUDED.balance,
                                           version = EXCLUDED.version
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            CountDownLatch readyGate = new CountDownLatch(4);

            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < 4; ++i) {

                Future<Integer> future = executor.submit(() -> {

                    readyGate.countDown();

                    try {
                        if (!startGate.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start gate timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Worker interrupted", e
                        );
                    }

                    return template.execute(status -> {

                        entityManager.createNativeQuery("""
                                        SELECT *
                                        FROM bankflow_optimistic_lab
                                        WHERE id = ?1
                                        FOR UPDATE
                                        """)
                                .setParameter(1, labId)
                                .getSingleResult();

                        int updatedRows = entityManager.createNativeQuery("""
                                        UPDATE bankflow_optimistic_lab
                                        SET balance = balance + 1
                                        WHERE id = ?1
                                        """)
                                .setParameter(1, labId)
                                .executeUpdate();

                        return updatedRows;
                    });
                });

                futures.add(future);
            }

            if (!readyGate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers not ready");
            }
            startGate.countDown();

            for (Future<Integer> future : futures) {
                Integer updatedRow = future.get(10, TimeUnit.SECONDS);
                assertEquals(1, updatedRow);
            }

            BigDecimal balance = (BigDecimal) template.execute(status ->
                    entityManager.createNativeQuery(
                                    """
                                            select balance from bankflow_optimistic_lab 
                                            where id = ?1
                                            """
                            )
                            .setParameter(1, labId)
                            .getSingleResult());

            assertEquals(0, new BigDecimal("1004").compareTo(balance));

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
                throw new AssertionError(
                        "Workers did not terminate; cleanup skipped"
                );
            }

            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }

    private void optimisticIncrement(Long labId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);
            entity.setBalance(entity.getBalance().add(BigDecimal.ONE));
            entityManager.flush();
        });
    }

    private void pessimisticIncrement(Long labId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            OptimisticLabEntity entity = entityManager
                    .find(OptimisticLabEntity.class, labId, LockModeType.PESSIMISTIC_WRITE);
            entity.setBalance(entity.getBalance().add(BigDecimal.ONE));
            entityManager.flush();
        });
    }

    @Test
    void shouldIncrementUsingBothLockingStrategies() {
        Long labId = 103L;
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                INSERT INTO bankflow_optimistic_lab
                                    (id, balance, version)
                                VALUES (?1, 1000, 0)
                                ON CONFLICT (id)
                                DO UPDATE SET
                                    balance = EXCLUDED.balance,
                                    version = EXCLUDED.version
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            optimisticIncrement(labId);
            pessimisticIncrement(labId);

            template.executeWithoutResult(status -> {
                BigDecimal balance = (BigDecimal) entityManager.createNativeQuery(
                                """
                                        select balance from bankflow_optimistic_lab 
                                        where id = ?1
                                        """
                        )
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(0, new BigDecimal("1002").compareTo(balance));

                Number version = (Number) entityManager.createNativeQuery("""
                                SELECT version
                                FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(2L, version.longValue());

            });
        } finally {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }

    @Test
    void fourWorkers_shouldObserveOptimisticConflicts() throws Exception {
        Long labId = 104L;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startGate = new CountDownLatch(1);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                INSERT INTO bankflow_optimistic_lab
                                    (id, balance, version)
                                VALUES (?1, 1000, 0)
                                ON CONFLICT (id)
                                DO UPDATE SET
                                    balance = EXCLUDED.balance,
                                    version = EXCLUDED.version
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            CountDownLatch readyStart = new CountDownLatch(4);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < 4; ++i) {
                Future<Integer> future = executor.submit(() -> {
                    readyStart.countDown();
                    if (!startGate.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("startGate Timeout");
                    }
                    optimisticIncrement(labId);
                    return 1;
                });
                futures.add(future);
            }

            if (!readyStart.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("readyStart timeout");
            }
            startGate.countDown();

            int successCount = 0;
            int conflictCount = 0;

            for (Future<Integer> future : futures) {
                try {
                    Integer result = future.get(10, TimeUnit.SECONDS);
                    assertEquals(1, result);
                    successCount++;
                } catch (ExecutionException e) {
                    if (isOptimisticFailure(e)) {
                        conflictCount++;
                    } else {
                        throw e;
                    }
                }
            }
            BigDecimal expectedBalance =
                    BigDecimal.valueOf(1000L + successCount);
            long expectedVersion = successCount;

            template.executeWithoutResult(status -> {
                BigDecimal balance = (BigDecimal) entityManager.createNativeQuery(
                                """
                                        select balance from bankflow_optimistic_lab where id = ?1
                                        """
                        )
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(0, expectedBalance.compareTo(balance));

                Number version = (Number) entityManager.createNativeQuery(
                                """
                                        select version from bankflow_optimistic_lab where id = ?1
                                        """
                        )
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(expectedVersion, version.longValue());
            });

            assertEquals(4, successCount + conflictCount);


        } finally {
            startGate.countDown();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                executor.shutdownNow();
                terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            }

            if (!terminated) {
                throw new AssertionError(
                        "Workers did not terminate; cleanup skipped"
                );
            }

            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        delete from bankflow_optimistic_lab where id = ?1
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });

        }
    }

    private static boolean isOptimisticFailure(Throwable error) {
        for (Throwable cause = error;
             cause != null;
             cause = cause.getCause()) {

            if (cause instanceof OptimisticLockException
                    || cause instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
        }

        return false;
    }

    @Test
    void fourWorkers_shouldDetectDeterministicOptimisticConflict() throws Exception {
        Long labId = 105L;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch allowUpdate = new CountDownLatch(1);
        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                INSERT INTO bankflow_optimistic_lab
                                    (id, balance, version)
                                VALUES (?1, 1000, 0)
                                ON CONFLICT (id)
                                DO UPDATE SET
                                    balance = EXCLUDED.balance,
                                    version = EXCLUDED.version
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            CountDownLatch allRead = new CountDownLatch(4);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < 4; ++i) {
                Future<Integer> future = executor.submit(() -> {
                    template.executeWithoutResult(status -> {
                        OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);

                        allRead.countDown();
                        try {
                            if (!allowUpdate.await(3, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("allowUpdate timeout");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted", e);
                        }

                        entity.setBalance(entity.getBalance().add(BigDecimal.ONE));
                        entityManager.flush();
                    });

                    return 1;
                });

                futures.add(future);
            }

            try {
                assertTrue(allRead.await(2, TimeUnit.SECONDS));
            } finally {
                allowUpdate.countDown();
            }

            int successCount = 0;
            int conflictCount = 0;

            for (Future<Integer> future : futures) {
                try {
                    Integer result = future.get(4, TimeUnit.SECONDS);
                    assertEquals(1, result);
                    successCount++;
                } catch (ExecutionException e) {
                    if (isOptimisticFailure(e)) {
                        conflictCount++;
                    } else {
                        throw e;
                    }
                }
            }

            BigDecimal expectedBalance = new BigDecimal("1001");
            Long expectedVersion = 1L;

            assertEquals(1, successCount);
            assertEquals(3, conflictCount);

            template.executeWithoutResult(status -> {
                OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);
                assertEquals(
                        0,
                        expectedBalance.compareTo(entity.getBalance())
                );
                assertEquals(expectedVersion, entity.getVersion().longValue());
            });

        } finally {
            allowUpdate.countDown();
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
                throw new AssertionError(
                        "Workers did not terminate; cleanup skipped"
                );
            }

            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                        """
                                delete from bankflow_optimistic_lab where id = ?1
                                """
                ).setParameter(1, labId).executeUpdate();
            });
        }
    }

    //helper retry
    private int optimisticIncrementWithRetry(Long labId, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }

        for (int i = 1; i <= maxAttempts; ++i) {
            try {
                optimisticIncrement(labId);
                return i;
            } catch (RuntimeException e) {
                if (!isOptimisticFailure(e)) {
                    throw e;
                }

                if (i == maxAttempts) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }


    @Test
    void optimisticRetry_shouldIncrementSuccessfully() {

        Long labId = 106L;

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                INSERT INTO bankflow_optimistic_lab
                                    (id, balance, version)
                                VALUES (?1, 1000, 0)
                                ON CONFLICT (id)
                                DO UPDATE SET
                                    balance = EXCLUDED.balance,
                                    version = EXCLUDED.version
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            int attempts = optimisticIncrementWithRetry(labId, 3);
            assertEquals(1, attempts);

            template.executeWithoutResult(status -> {
                OptimisticLabEntity entity =
                        entityManager.find(OptimisticLabEntity.class, labId);
                assertEquals(
                        0,
                        new BigDecimal("1001")
                                .compareTo(entity.getBalance())
                );
                assertEquals(1L, entity.getVersion().longValue());
            });

            assertThrows(
                    IllegalArgumentException.class,
                    () -> optimisticIncrementWithRetry(106L, 0)
            );

        } finally {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }

    @Test
    void fourWorkers_shouldIncrementSuccessfullyWithRetry() throws Exception {
        Long labId = 107L;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startGate = new CountDownLatch(1);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        insert into bankflow_optimistic_lab (id, balance, version) 
                                        values (?1, 1000, 0) 
                                        on conflict (id) 
                                        do update set balance = EXCLUDED.balance, 
                                                      version = EXCLUDED.version 
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            CountDownLatch readyGate = new CountDownLatch(4);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < 4; ++i) {
                Future<Integer> future = executor.submit(() -> {
                    readyGate.countDown();
                    if (!startGate.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("startGate timeout");
                    }
                    return optimisticIncrementWithRetry(labId, 10);
                });
                futures.add(future);
            }

            try {
                assertTrue(readyGate.await(3, TimeUnit.SECONDS));
            } finally {
                startGate.countDown();
            }

            int totalAttempts = 0;

            for (Future<Integer> future : futures) {
                int attempts = future.get(15, TimeUnit.SECONDS);

                assertTrue(attempts >= 1 && attempts <= 10);
                totalAttempts += attempts;
            }

            System.out.println("Total attempts = " + totalAttempts);
            System.out.println("Total retries = " + (totalAttempts - 4));

            template.executeWithoutResult(status -> {
                OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);
                assertEquals(0, new BigDecimal("1004").compareTo(entity.getBalance()));
                assertEquals(4L, entity.getVersion().longValue());
            });

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
                throw new AssertionError("Executor terminated failed");
            }

            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        delete from bankflow_optimistic_lab where id = ?1
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });

        }
    }

    private record OperationResult(
            int attempts,
            long latencyNanos
    ) {
    }

    @Test
    void measureOptimisticLatency() throws Exception {
        Long labId = 108L;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        insert into bankflow_optimistic_lab (id, balance, version) 
                                        values (?1, 1000, 0) 
                                        on conflict (id) 
                                        do update set balance = EXCLUDED.balance, 
                                                      version = EXCLUDED.version 
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            CountDownLatch readyStart = new CountDownLatch(4);
            List<Future<OperationResult>> futures = new ArrayList<>();
            long totalAttempts = 0;
            List<Long> latencies = new ArrayList<>();

            for (int i = 0; i < 4; ++i) {
                Future<OperationResult> future = executor.submit(() -> {
                    readyStart.countDown();
                    try {
                        if (!startGate.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start gate timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Worker interrupted", e);
                    }

                    long start = System.nanoTime();
                    int attempts = optimisticIncrementWithRetry(labId, 10);

                    return new OperationResult(attempts, System.nanoTime() - start);
                });

                futures.add(future);
            }

            long groupStart;

            try {
                assertTrue(readyStart.await(3, TimeUnit.SECONDS));
            } finally {
                groupStart = System.nanoTime();
                startGate.countDown();
            }

            for (Future<OperationResult> future : futures) {
                OperationResult result = future.get(10, TimeUnit.SECONDS);

                latencies.add(result.latencyNanos);
                totalAttempts += result.attempts;
            }

            long totalElapsedNanos = System.nanoTime() - groupStart;

            double avgLatencyMs = latencies.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElseThrow() / 1000000.0;

            double throughput = 4.0 / (totalElapsedNanos / 1000000000.0);

            System.out.printf("Optimistic avg latency: %.3f ms%n", avgLatencyMs);
            System.out.printf("Optimistic throughput: %.2f ops/s%n", throughput);
            System.out.printf("Optimistic retries: %d%n", totalAttempts - 4);

            template.executeWithoutResult(status -> {
                OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);
                assertEquals(0, new BigDecimal("1004").compareTo(entity.getBalance()));
                assertEquals(4L, entity.getVersion().longValue());
            });

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
                throw new AssertionError("Executor terminated failed");
            }

            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                """
                                        delete from bankflow_optimistic_lab where id = ?1
                                        """
                        )
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }


    @Test
    void measurePessimisticLatency() throws Exception {
        Long labId = 109L;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                INSERT INTO bankflow_optimistic_lab (id, balance, version)
                                VALUES (?1, 1000, 0)
                                ON CONFLICT (id)
                                DO UPDATE SET
                                    balance = EXCLUDED.balance,
                                    version = EXCLUDED.version
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });

            CountDownLatch readyGate = new CountDownLatch(4);
            List<Future<OperationResult>> futures = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                Future<OperationResult> future = executor.submit(() -> {
                    readyGate.countDown();

                    try {
                        if (!startGate.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start gate timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Worker interrupted", e);
                    }

                    long start = System.nanoTime();

                    pessimisticIncrement(labId);

                    return new OperationResult(
                            1,
                            System.nanoTime() - start
                    );
                });

                futures.add(future);
            }

            long groupStart;
            try {
                assertTrue(
                        readyGate.await(10, TimeUnit.SECONDS),
                        "Workers were not ready"
                );
            } finally {
                groupStart = System.nanoTime();
                startGate.countDown();
            }

            List<Long> latencies = new ArrayList<>();
            int totalAttempts = 0;

            for (Future<OperationResult> future : futures) {
                OperationResult result = future.get(15, TimeUnit.SECONDS);

                assertEquals(1, result.attempts());

                latencies.add(result.latencyNanos());
                totalAttempts += result.attempts();
            }

            long totalElapsedNanos = System.nanoTime() - groupStart;

            double avgLatencyMs = latencies.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElseThrow() / 1_000_000.0;

            double throughput =
                    4.0 / (totalElapsedNanos / 1_000_000_000.0);

            System.out.printf(
                    "Pessimistic avg latency: %.3f ms%n",
                    avgLatencyMs
            );
            System.out.printf(
                    "Pessimistic throughput: %.2f ops/s%n",
                    throughput
            );
            System.out.printf(
                    "Pessimistic retries: %d%n",
                    totalAttempts - 4
            );

            template.executeWithoutResult(status -> {
                OptimisticLabEntity entity =
                        entityManager.find(OptimisticLabEntity.class, labId);

                assertEquals(
                        0,
                        new BigDecimal("1004").compareTo(entity.getBalance())
                );
                assertEquals(4L, entity.getVersion().longValue());
            });

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
                throw new AssertionError(
                        "Workers did not terminate; cleanup skipped"
                );
            }

            template.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                DELETE FROM bankflow_optimistic_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .executeUpdate();
            });
        }
    }


}
