package com.bankflow.concurrency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DeadlockIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentTransaction_shouldDetectDeadlock() throws InterruptedException {

        Long labId10 = 10L;
        long labId20 = 20L;
        CountDownLatch startGate = new CountDownLatch(1);
        Thread threadA = null;
        Thread threadB = null;
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                                INSERT INTO bankflow_deadlock_lab (id, balance)
                                VALUES (10, 1000000), (20, 1000000)
                                ON CONFLICT (id)
                                DO UPDATE SET balance = EXCLUDED.balance
                                """)
                        .executeUpdate();
            });

            CountDownLatch bothLock = new CountDownLatch(2);
            AtomicReference<Throwable> errorA = new AtomicReference<>();
            AtomicReference<Throwable> errorB = new AtomicReference<>();

            threadA = new Thread(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        entityManager.createNativeQuery(
                                "SET LOCAL lock_timeout = '8s'"
                        ).executeUpdate();
                        entityManager.createNativeQuery(
                                        """
                                                select * from bankflow_deadlock_lab where id = ?1 for update 
                                                """)
                                .setParameter(1, labId10)
                                .getSingleResult();
                        bothLock.countDown();
                        try {
                            if (!startGate.await(3, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting for startGate");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted waiting for startGate", e);
                        }
                        entityManager.createNativeQuery(
                                        """
                                                select * from bankflow_deadlock_lab where id = ?1 for update 
                                                """
                                )
                                .setParameter(1, labId20)
                                .getResultList();
                    });
                } catch (Throwable e) {
                    errorA.set(e);
                }

            }, "Thread-A");

            threadB = new Thread(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        entityManager.createNativeQuery(
                                "SET LOCAL lock_timeout = '8s'"
                        ).executeUpdate();
                        entityManager.createNativeQuery(
                                        """
                                                select * from bankflow_deadlock_lab where id = ?1 for update 
                                                """)
                                .setParameter(1, labId20)
                                .getSingleResult();
                        bothLock.countDown();
                        try {
                            if (!startGate.await(3, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting for startGate");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted waiting for startGate", e);
                        }
                        entityManager.createNativeQuery(
                                        """
                                                select * from bankflow_deadlock_lab where id = ?1 for update 
                                                """
                                )
                                .setParameter(1, labId10)
                                .getResultList();
                    });
                } catch (Throwable e) {
                    errorB.set(e);
                }

            }, "Thread-B");

            threadA.start();
            threadB.start();

            boolean locked;
            try {
                locked = bothLock.await(3, TimeUnit.SECONDS);
            } finally {
                startGate.countDown();
            }

            threadA.join(10000);
            threadB.join(10000);

            assertTrue(locked);
            assertFalse(threadA.isAlive());
            assertFalse(threadB.isAlive());

            boolean aDeadlocked = isDeadlock(errorA.get());
            boolean bDeadlocked = isDeadlock(errorB.get());

            assertTrue(
                    aDeadlocked || bDeadlocked,
                    "Expected SQLSTATE 40P01"
                            + "\nThread A error: " + errorA.get()
                            + "\nThread B error: " + errorB.get()
            );
            assertTrue(
                    (aDeadlocked && errorB.get() == null)
                            || (bDeadlocked && errorA.get() == null),
                    "Expected exactly one deadlock victim and one successful transaction"
            );

        } finally {
            startGate.countDown();

            if (threadA != null
                    && threadB != null
                    && !threadA.isAlive()
                    && !threadB.isAlive()) {
                transactionTemplate.executeWithoutResult(status -> {
                    entityManager.createNativeQuery("""
                DELETE FROM bankflow_deadlock_lab
                WHERE id IN (10, 20)
                """)
                            .executeUpdate();
                });
            }
        }
    }

    @Test
    void consistentLockOrdering_shouldPreventDeadlock()
            throws InterruptedException {

        long accountA = 10L;
        long accountB = 20L;

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                INSERT INTO bankflow_deadlock_lab (id, balance)
                VALUES (10, 1000000), (20, 1000000)
                ON CONFLICT (id)
                DO UPDATE SET balance = EXCLUDED.balance
                """)
                    .executeUpdate();
        });

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        AtomicReference<String> completedA = new AtomicReference<>();
        AtomicReference<String> completedB = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try {
                readyGate.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Thread A start timeout");
                }

                template.executeWithoutResult(status -> {
                    entityManager.createNativeQuery(
                            "SET LOCAL lock_timeout = '8s'"
                    ).executeUpdate();

                    lockAccount(accountA);
                    lockAccount(accountB);
                });

                completedA.set("SUCCESS");

            } catch (Throwable e) {
                errorA.set(e);
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try {
                readyGate.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Thread B start timeout");
                }

                template.executeWithoutResult(status -> {
                    entityManager.createNativeQuery(
                            "SET LOCAL lock_timeout = '8s'"
                    ).executeUpdate();

                    lockAccount(accountA);
                    lockAccount(accountB);
                });

                completedB.set("SUCCESS");

            } catch (Throwable e) {
                errorB.set(e);
            }
        }, "Thread-B");

        try {

            threadA.start();
            threadB.start();

            assertTrue(
                    readyGate.await(5, TimeUnit.SECONDS),
                    "Workers did not become ready"
            );

            startGate.countDown();

            threadA.join(10000);
            threadB.join(10000);

            assertFalse(threadA.isAlive(), "Thread A is still alive");
            assertFalse(threadB.isAlive(), "Thread B is still alive");

            assertNull(errorA.get(), "Thread A failed: " + errorA.get());
            assertNull(errorB.get(), "Thread B failed: " + errorB.get());

            assertEquals("SUCCESS", completedA.get());
            assertEquals("SUCCESS", completedB.get());

        } finally {
            startGate.countDown();

            if (!threadA.isAlive() && !threadB.isAlive()) {
                template.executeWithoutResult(status -> {
                    entityManager.createNativeQuery("""
                        DELETE FROM bankflow_deadlock_lab
                        WHERE id IN (10, 20)
                        """)
                            .executeUpdate();
                });
            }
        }
    }

    private static boolean isDeadlock(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && "40P01".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private void lockAccount(Long accountId) {
        entityManager.createNativeQuery("""
            SELECT id
            FROM bankflow_deadlock_lab
            WHERE id = ?1
            FOR UPDATE
            """)
                .setParameter(1, accountId)
                .getSingleResult();
    }

}
