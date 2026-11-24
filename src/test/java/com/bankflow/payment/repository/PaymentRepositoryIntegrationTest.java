package com.bankflow.payment.repository;

import com.bankflow.payment.domain.PaymentStatus;
import com.bankflow.payment.entity.PaymentEntity;
import com.bankflow.transfer.enums.Currency;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentRepositoryIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldPersistPendingPayment() {
        String merchantReference = "payment-repo-" + UUID.randomUUID();
        PaymentEntity payment = PaymentEntity.createPending(
                new BigDecimal("100000.00"),
                Currency.VND,
                merchantReference
        );

        PaymentEntity saved = paymentRepository.save(payment);

        paymentRepository.flush();
        entityManager.clear();

        PaymentEntity reloaded = paymentRepository.findById(
                saved.getId()
        ).orElseThrow();

        assertNotNull(reloaded.getId());

        assertEquals(
                PaymentStatus.PENDING,
                reloaded.getStatus()
        );

        assertEquals(
                0,
                new BigDecimal("100000.00")
                        .compareTo(reloaded.getAmount())
        );

        assertEquals(Currency.VND, reloaded.getCurrency());
        assertEquals(merchantReference, reloaded.getMerchantReference());

        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
        assertNotNull(reloaded.getVersion());

        assertEquals(0L, reloaded.getVersion());
    }

    @Test
    void shouldRejectInvalidPaymentStatusAtDatabaseLevel() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO payments (
                            amount,
                            currency,
                            status,
                            merchant_reference
                        )
                        VALUES (?, ?, ?, ?)
                        """,
                        new BigDecimal("100000.00"),
                        "VND",
                        "INVALID",
                        "invalid-status-" + UUID.randomUUID())
        );
    }
}
