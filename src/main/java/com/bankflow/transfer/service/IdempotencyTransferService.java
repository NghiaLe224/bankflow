package com.bankflow.transfer.service;

import com.bankflow.common.exception.IdempotencyConflictException;
import com.bankflow.common.exception.TransferNotFoundException;
import com.bankflow.common.exception.UserNotFoundException;
import com.bankflow.security.CurrentUserService;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.IdempotencyRecordEntity;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.mapper.TransferMapper;
import com.bankflow.transfer.repository.IdempotencyRecordRepository;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdempotencyTransferService {

    private final TransferService transferService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransferRequestFingerprint transferRequestFingerprint;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final TransferRepository transferRepository;
    private final TransferMapper transferMapper;

    public IdempotencyTransferService(TransferService transferService, IdempotencyRecordRepository idempotencyRecordRepository, TransferRequestFingerprint transferRequestFingerprint, CurrentUserService currentUserService, UserRepository userRepository, TransferRepository transferRepository, TransferMapper transferMapper) {
        this.transferService = transferService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transferRequestFingerprint = transferRequestFingerprint;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.transferRepository = transferRepository;
        this.transferMapper = transferMapper;
    }

    @Transactional
    public TransferResponse create(CreateTransferRequest request, String idempotencyKey) {
        Long currentUserId = currentUserService.getCurrentUserId();
        String fingerprint = transferRequestFingerprint.fingerprint(request);
        Optional<IdempotencyRecordEntity> existingRecord = idempotencyRecordRepository
                .findByUser_IdAndIdempotencyKey(currentUserId, idempotencyKey);

        if (existingRecord.isPresent()) {
            IdempotencyRecordEntity record = existingRecord.get();
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException("Idempotency key already used with different request");
            }
            return transferMapper.toResponse(record.getTransfer());
        }

        TransferResponse transferResponse = transferService.createTransfer(request);
        UserEntity user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));
        TransferEntity transfer = transferRepository.findById(transferResponse.id())
                .orElseThrow(() -> new TransferNotFoundException(transferResponse.id()));
        idempotencyRecordRepository.save(new IdempotencyRecordEntity(
                user,
                idempotencyKey,
                fingerprint,
                transfer
        ));
        return transferResponse;
    }
}
