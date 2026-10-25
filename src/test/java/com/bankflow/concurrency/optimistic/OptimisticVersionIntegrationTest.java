package com.bankflow.concurrency.optimistic;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE"
})
public class OptimisticVersionIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void shouldLoadVersionedEntityFromDatabase() {
        OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, 1L);

        assertNotNull(entity);
        assertEquals(0, new BigDecimal("1000")
                .compareTo(entity.getBalance()));
        assertEquals(1L, entity.getVersion());
    }

    @Test
    @Transactional
    void changingManagedEntity_shouldIncrementVersionOnFlush() {
        OptimisticLabEntity entity
                = entityManager.find(OptimisticLabEntity.class, 1L);

        assertEquals(0, new BigDecimal("1000")
                .compareTo(entity.getBalance()));
        assertEquals(1L, entity.getVersion());

        entity.setBalance(new BigDecimal("900"));

        entityManager.flush();

        assertEquals(2L, entity.getVersion());

        entityManager.clear();

        OptimisticLabEntity updatedEntity =
                entityManager.find(OptimisticLabEntity.class, 1L);

        assertEquals(0, new BigDecimal("900")
                .compareTo(updatedEntity.getBalance()));
        assertEquals(2L, updatedEntity.getVersion());
    }

    @Test
    void twoTransactions_shouldReadSameInitialVersion() throws InterruptedException {
        TransactionTemplate template
                = new TransactionTemplate(transactionManager);

        AtomicReference<Long> versionA = new AtomicReference<>();
        AtomicReference<Long> versionB = new AtomicReference<>();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch releaseGate = new CountDownLatch(1);
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try {
                template.execute(
                        status -> {
                            try {
                                OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, 1L);
                                versionA.set(entity.getVersion());
                                bothRead.countDown();
                                releaseGate.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "Worker interrupted while waiting for releaseGate", e
                                );
                            }
                            return null;
                        }
                );
            } catch (Throwable e) {
                errorA.set(e);
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                template.execute(
                        status -> {
                            try {
                                OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, 1L);
                                versionB.set(entity.getVersion());
                                bothRead.countDown();
                                releaseGate.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "Worker interrupted while waiting for releaseGate", e
                                );
                            }
                            return null;
                        }
                );
            } catch (Throwable e) {
                errorB.set(e);
            }

        });

        threadA.start();
        threadB.start();

        boolean read;
        try {
            read = bothRead.await(3, TimeUnit.SECONDS);
        } finally {
            releaseGate.countDown();
        }

        threadA.join(3000);
        threadB.join(3000);

        assertTrue(read);
        assertFalse(threadA.isAlive(), "Thread A is still running");
        assertFalse(threadB.isAlive(), "Thread B is still running");
        assertNull(errorA.get(), "Thread A failed");
        assertNull(errorB.get(), "Thread B failed");
        assertEquals(1L, versionA.get());
        assertEquals(1L, versionB.get());
    }

    @Test
    void twoTransactions_shouldDetectOptimisticLockConflict()
            throws InterruptedException {
        // 1. prepare fixture for test
        Long labId = 2L;

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.executeWithoutResult(
                status -> {
                    entityManager.createNativeQuery(
                                    """
                                            insert into bankflow_optimistic_lab (id, balance, version) values (:id, 1000, 1) 
                                            on conflict (id) do update set balance = 1000, version = 1
                                            """
                            ).setParameter("id", labId)
                            .executeUpdate();
                });

        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        AtomicReference<Long> versionA = new AtomicReference<>();
        AtomicReference<Long> versionB = new AtomicReference<>();

        AtomicBoolean aCommitted = new AtomicBoolean(false);

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowA = new CountDownLatch(1);
        CountDownLatch aFinished = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            try {
                template.executeWithoutResult(
                        status -> {
                            OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);
                            versionA.set(entity.getVersion());

                            bothRead.countDown();

                            try {
                                if (!allowA.await(3, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Timed out waiting for allowA");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted waiting for allowA", e);
                            }

                            entity.setBalance(new BigDecimal("900"));
                            entityManager.flush();
                        }
                );

                aCommitted.set(true);

            } catch (Throwable e) {
                errorA.set(e);
            } finally {
                aFinished.countDown();
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                template.executeWithoutResult(
                        status -> {
                            OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);
                            versionB.set(entity.getVersion());

                            bothRead.countDown();

                            try {
                                if (!aFinished.await(3, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Timed out waiting for aFinished");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted waiting for aFinished", e);
                            }

                            if (!aCommitted.get()) {
                                throw new IllegalStateException("Transaction A did not committed");
                            }

                            entityManager.createNativeQuery("""
                                            insert into bankflow_optimistic_audit_lab (id, description) 
                                            values (?1, ?2)
                                            """)
                                    .setParameter(1, labId)
                                    .setParameter(2, "Transaction B attempted update")
                                    .executeUpdate();

                            entity.setBalance(new BigDecimal("800"));
                            entityManager.flush();
                        });

            } catch (Throwable e) {
                errorB.set(e);
            }
        });

        try {
            threadA.start();
            threadB.start();

            boolean read;
            try {
                read = bothRead.await(3, TimeUnit.SECONDS);
            } finally {
                allowA.countDown();
            }

            threadA.join(3000);
            threadB.join(3000);

            assertTrue(read);
            assertFalse(threadA.isAlive());
            assertFalse(threadB.isAlive());
            assertEquals(Long.valueOf(1L), versionA.get());
            assertEquals(Long.valueOf(1L), versionB.get());
            assertNull(errorA.get());
            assertNotNull(errorB.get());
            assertTrue(isOptimisticFailure(errorB.get()));
            assertTrue(aCommitted.get(), "Transaction A did not commit");

            template.executeWithoutResult(status -> {
                OptimisticLabEntity entity = entityManager.find(OptimisticLabEntity.class, labId);

                assertEquals(0, new BigDecimal("900")
                        .compareTo(entity.getBalance()));
                assertEquals(Long.valueOf(2L), entity.getVersion());
            });

            template.executeWithoutResult(status -> {

                Object count = entityManager.createNativeQuery("""
                                SELECT COUNT(*)
                                FROM bankflow_optimistic_audit_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(0L, ((Number) count).longValue());
            });


        } finally {
            allowA.countDown();
            if (!threadA.isAlive() && !threadB.isAlive()) {
                template.executeWithoutResult(status -> {
                    entityManager.createNativeQuery(
                                    "delete from bankflow_optimistic_lab where id = ?1"
                            )
                            .setParameter(1, labId)
                            .executeUpdate();
                });
            }
        }
    }

    private static boolean isOptimisticFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }
}
