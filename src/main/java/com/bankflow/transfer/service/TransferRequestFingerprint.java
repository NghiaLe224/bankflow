
package com.bankflow.transfer.service;

import com.bankflow.transfer.dto.CreateTransferRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class TransferRequestFingerprint {

    public String fingerprint(CreateTransferRequest request) {

        String normalizedAmount = request.amount()
                .stripTrailingZeros()
                .toPlainString();
        String canonical = String.join("|",
                request.fromAccountId().toString(),
                request.toAccountId().toString(),
                normalizedAmount,
                request.currency().name()
        );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", e
            );
        }
    }
}
