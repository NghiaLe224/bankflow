package com.bankflow.payment.provider;


import com.bankflow.payment.provider.fake.FakePaymentProvider;
import com.bankflow.transfer.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class FakePaymentProviderTest {

    private FakePaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FakePaymentProvider();
    }

    private ProviderPaymentRequest request(
            String reference,
            String amount,
            Currency currency
    ) {
        return new ProviderPaymentRequest(
                reference,
                new BigDecimal(amount),
                currency
        );
    }

    @Test
    void charge_newReference_shouldReturnSuccess() {
        var result = provider.charge(
                request("ORDER-001", "100000", Currency.VND)
        );

        assertEquals(ProviderPaymentStatus.SUCCESS, result.status());
        assertNotNull(result.providerReference());
    }

    @Test
    void charge_sameReference_shouldReturnSameResult() {
        var first = provider.charge(
                request("ORDER-001", "100000", Currency.VND)
        );

        var second = provider.charge(
                request("ORDER-001", "100000.00", Currency.VND)
        );

        assertEquals(
                first.providerReference(),
                second.providerReference()
        );
    }

    @Test
    void charge_differentAmount_shouldThrow() {
        provider.charge(
                request("ORDER-001", "100000", Currency.VND)
        );

        assertThrows(IllegalArgumentException.class, () ->
                provider.charge(
                        request("ORDER-001", "200000", Currency.VND)
                )
        );
    }

    @Test
    void charge_differentCurrency_shouldThrow() {
        provider.charge(
                request("ORDER-001", "100000", Currency.VND)
        );

        assertThrows(IllegalArgumentException.class, () ->
                provider.charge(
                        request("ORDER-001", "100000", Currency.USD)
                )
        );
    }

    @Test
    void charge_invalidAmount_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                provider.charge(
                        new ProviderPaymentRequest(
                                "ORDER-001", null, Currency.VND
                        )
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                provider.charge(
                        request("ORDER-001", "0", Currency.VND)
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                provider.charge(
                        request("ORDER-001", "-100", Currency.VND)
                )
        );
    }

    @Test
    void getStatus_existingReference_shouldReturnResult() {
        var first = provider.charge(
                request("ORDER-001", "100000", Currency.VND)
        );

        var found = provider.getStatus("ORDER-001");

        assertEquals(
                first.providerReference(),
                found.providerReference()
        );
        assertEquals(first.status(), found.status());
    }

    @Test
    void getStatus_unknownReference_shouldThrow() {
        assertThrows(NoSuchElementException.class, () ->
                provider.getStatus("NOT-FOUND")
        );
    }

    @Test
    void concurrentCharge_sameReference_shouldReturnSameProviderReference()
            throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        var paymentRequest =
                request("ORDER-RACE", "100000", Currency.VND);

        Callable<ProviderPaymentResult> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timeout");
            }

            return provider.charge(paymentRequest);
        };

        Future<ProviderPaymentResult> futureA = executor.submit(task);
        Future<ProviderPaymentResult> futureB = executor.submit(task);

        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            ProviderPaymentResult resultA =
                    futureA.get(10, TimeUnit.SECONDS);

            ProviderPaymentResult resultB =
                    futureB.get(10, TimeUnit.SECONDS);

            assertEquals(
                    resultA.providerReference(),
                    resultB.providerReference()
            );

            assertEquals(
                    ProviderPaymentStatus.SUCCESS,
                    resultA.status()
            );
            assertEquals(
                    ProviderPaymentStatus.SUCCESS,
                    resultB.status()
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}

