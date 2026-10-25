package com.bankflow.concurrency;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentDebitSimulationTest {

    @Test
    void twoConcurrentDebits_shouldExposeLostUpdate() throws InterruptedException {
        AtomicReference<BigDecimal> balance = new AtomicReference<>(new BigDecimal("1000"));
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch writeGate = new CountDownLatch(1);

        Runnable debitTask = () -> {
            BigDecimal snapshot = balance.get();
            bothRead.countDown();

            try {
                writeGate.await();
                BigDecimal newBalance = snapshot.subtract(new BigDecimal("100"));
                balance.set(newBalance);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread threadA = new Thread(debitTask, "debit-A");
        Thread threadB = new Thread(debitTask, "debit-B");

        threadA.start();
        threadB.start();

        try {
            assertTrue(bothRead.await(3, TimeUnit.SECONDS));
        } finally {
            writeGate.countDown();
        }

        threadA.join(3000);
        threadB.join(3000);

        assertFalse(threadA.isAlive());
        assertFalse(threadB.isAlive());
        assertEquals(0, new BigDecimal("900").compareTo(balance.get()));
    }
}
