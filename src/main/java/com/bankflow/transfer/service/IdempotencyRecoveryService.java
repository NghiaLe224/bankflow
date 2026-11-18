package com.bankflow.transfer.service;

import com.bankflow.common.exception.IdempotencyConflictException;
import com.bankflow.security.CurrentUserService;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.IdempotencyRecordEntity;
import com.bankflow.transfer.mapper.TransferMapper;
import com.bankflow.transfer.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyRecoveryService {
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final CurrentUserService currentUserService;
    private final TransferRequestFingerprint transferRequestFingerprint;
    private final TransferMapper transferMapper;

    public IdempotencyRecoveryService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            CurrentUserService currentUserService,
            TransferRequestFingerprint transferRequestFingerprint,
            TransferMapper transferMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.currentUserService = currentUserService;
        this.transferRequestFingerprint = transferRequestFingerprint;
        this.transferMapper = transferMapper;
    }

    @Transactional(readOnly = true)
    public TransferResponse recover(
            CreateTransferRequest request,
            String idempotencyKey
    ) {
        Long currentUserId = currentUserService.getCurrentUserId();
        String fingerprint = transferRequestFingerprint.fingerprint(request);
        IdempotencyRecordEntity record = idempotencyRecordRepository.findByUser_IdAndIdempotencyKey(currentUserId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record not found during recovery"));

        if (!fingerprint.equals(record.getRequestFingerprint())) {
            throw new IdempotencyConflictException("Idempotency key already used for another request");
        }

        return transferMapper.toResponse(record.getTransfer());
    }
}
