package com.bankflow.transfer.service;

import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyTransferService {

    private final IdempotencyTransferAttemptService idempotencyTransferAttemptService;
    private final IdempotencyRecoveryService idempotencyRecoveryService;

    public IdempotencyTransferService(
            IdempotencyTransferAttemptService idempotencyTransferAttemptService,
            IdempotencyRecoveryService idempotencyRecoveryService
    ) {
        this.idempotencyTransferAttemptService = idempotencyTransferAttemptService;
        this.idempotencyRecoveryService = idempotencyRecoveryService;
    }

    public TransferResponse create(
            CreateTransferRequest request,
            String idempotencyKey
    ) {
        try {
            return idempotencyTransferAttemptService.execute(request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            if (!isIdempotencyUniqueViolation(e)) {
                throw e;
            }
            return idempotencyRecoveryService.recover(request, idempotencyKey);
        }
    }

    private boolean isIdempotencyUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                if ("23505".equals(cve.getSQLState()) && "uq_user_id_idempotency_key".equals(cve.getConstraintName())) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
