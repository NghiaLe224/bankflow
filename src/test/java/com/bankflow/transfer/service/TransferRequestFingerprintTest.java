package com.bankflow.transfer.service;

import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TransferRequestFingerprintTest {

    private final TransferRequestFingerprint fingerprintService =
            new TransferRequestFingerprint();

    @Test
    void samePayload_shouldGenerateSameFingerprint() {
        CreateTransferRequest firstRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000"), Currency.VND);

        CreateTransferRequest secondRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000"), Currency.VND);

        String fingerprint1 = fingerprintService.fingerprint(firstRequest);
        String fingerprint2 = fingerprintService.fingerprint(secondRequest);

        assertEquals(fingerprint1, fingerprint2);
        assertEquals(64, fingerprint1.length());
    }

    @Test
    void differentAmountScale_shouldGenerateSameFingerprint() {
        CreateTransferRequest firstRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000"), Currency.VND);

        CreateTransferRequest secondRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000.00"), Currency.VND);

        String fingerprint1 = fingerprintService.fingerprint(firstRequest);
        String fingerprint2 = fingerprintService.fingerprint(secondRequest);

        assertEquals(fingerprint1, fingerprint2);
        assertEquals(64, fingerprint1.length());
    }

    @Test
    void differentAmount_shouldGenerateDifferentFingerprint() {
        CreateTransferRequest firstRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000"), Currency.VND);

        CreateTransferRequest secondRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("2000000"), Currency.VND);

        String fingerprint1 = fingerprintService.fingerprint(firstRequest);
        String fingerprint2 = fingerprintService.fingerprint(secondRequest);

        assertNotEquals(fingerprint1, fingerprint2);
        assertEquals(64, fingerprint1.length());
        assertEquals(64, fingerprint2.length());
    }

    @Test
    void differentCurrency_shouldGenerateDifferentFingerprint() {
        CreateTransferRequest firstRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000"), Currency.VND);

        CreateTransferRequest secondRequest =
                new CreateTransferRequest(1L, 2L, new BigDecimal("1000000"), Currency.USD);

        String fingerprint1 = fingerprintService.fingerprint(firstRequest);
        String fingerprint2 = fingerprintService.fingerprint(secondRequest);

        assertNotEquals(fingerprint1, fingerprint2);
        assertEquals(64, fingerprint1.length());
        assertEquals(64, fingerprint2.length());
    }
}
