package com.bankflow.concurrency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LostUpdateDatabaseIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentDebits_shouldReproduceLostUpdate()
            throws InterruptedException {

        Long labId = 1L;

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO bankflow_lost_update_lab
                                (id, balance)
                            VALUES (:id, 1000000)
                            ON CONFLICT (id)
                            DO UPDATE SET balance = 1000000
                            """)
                    .setParameter("id", labId)
                    .executeUpdate();
        });

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch aFinished = new CountDownLatch(1);
        CountDownLatch allowA = new CountDownLatch(1);

        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        AtomicReference<BigDecimal> balanceA = new AtomicReference<>();
        AtomicReference<BigDecimal> balanceB = new AtomicReference<>();

        AtomicBoolean aCommitted = new AtomicBoolean(false);

        Thread threadA = new Thread(() -> {
            try {
                template.executeWithoutResult(status -> {
                    BigDecimal balance = (BigDecimal) entityManager.createNativeQuery("""
                                    SELECT balance
                                    FROM bankflow_lost_update_lab
                                    WHERE id = ?1
                                    """)
                            .setParameter(1, labId)
                            .getSingleResult();
                    balanceA.set(balance);

                    bothRead.countDown();
                    try {
                        if (!allowA.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out with allowA");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted with allowA", e);
                    }

                    BigDecimal newBalance =
                            balance.subtract(new BigDecimal("100000"));

                    entityManager.createNativeQuery("""
                                    UPDATE bankflow_lost_update_lab
                                    SET balance = ?1
                                    WHERE id = ?2
                                    """)
                            .setParameter(1, newBalance)
                            .setParameter(2, labId)
                            .executeUpdate();
                });

                aCommitted.set(true);

            } catch (Throwable e) {
                errorA.set(e);
            } finally {
                aFinished.countDown();
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                template.executeWithoutResult(status -> {
                    BigDecimal balance = (BigDecimal) entityManager.createNativeQuery("""
                                    SELECT balance
                                    FROM bankflow_lost_update_lab
                                    WHERE id = ?1
                                    """)
                            .setParameter(1, labId)
                            .getSingleResult();
                    balanceB.set(balance);

                    bothRead.countDown();

                    try {
                        if (!aFinished.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out with aFinished");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted with aFinished", e);
                    }

                    if (!aCommitted.get()) {
                        throw new IllegalStateException("Transaction A didn't commit");
                    }

                    BigDecimal newBalance =
                            balance.subtract(new BigDecimal("100000"));

                    entityManager.createNativeQuery("""
                                    UPDATE bankflow_lost_update_lab
                                    SET balance = ?1
                                    WHERE id = ?2
                                    """)
                            .setParameter(1, newBalance)
                            .setParameter(2, labId)
                            .executeUpdate();
                });
            } catch (Throwable e) {
                errorB.set(e);
            }
        });

        try {
            threadA.start();
            threadB.start();

            boolean ready;
            try {
                ready = bothRead.await(5, TimeUnit.SECONDS);
            } finally {
                allowA.countDown();
            }

            threadA.join(7000);
            threadB.join(7000);

            assertTrue(ready);

            assertFalse(threadA.isAlive());
            assertFalse(threadB.isAlive());

            assertNull(errorA.get());
            assertNull(errorB.get());

            assertEquals(
                    0,
                    new BigDecimal("1000000")
                            .compareTo(balanceA.get())
            );

            assertEquals(
                    0,
                    new BigDecimal("1000000")
                            .compareTo(balanceB.get())
            );

            assertTrue(aCommitted.get());

            template.executeWithoutResult(status -> {
                BigDecimal actualBalance = (BigDecimal) entityManager.createNativeQuery("""
                                SELECT balance
                                FROM bankflow_lost_update_lab
                                WHERE id = ?1
                                """)
                        .setParameter(1, labId)
                        .getSingleResult();

                assertEquals(
                        0,
                        new BigDecimal("900000")
                                .compareTo(actualBalance)
                );
            });

        } finally {
            allowA.countDown();

            if (!threadA.isAlive() && !threadB.isAlive()) {
                template.executeWithoutResult(status -> {
                    entityManager.createNativeQuery("""
                                    DELETE FROM bankflow_lost_update_lab
                                    WHERE id = ?1
                                    """)
                            .setParameter(1, labId)
                            .executeUpdate();
                });
            }
        }
    }


}
