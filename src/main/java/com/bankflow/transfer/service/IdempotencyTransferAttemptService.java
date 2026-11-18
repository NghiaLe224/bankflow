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
public class IdempotencyTransferAttemptService {

    private final TransferRequestFingerprint transferRequestFingerprint;
    private final CurrentUserService currentUserService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransferMapper transferMapper;
    private final TransferRepository transferRepository;
    private final TransferService transferService;
    private final UserRepository userRepository;
    private final IdempotencyRaceHook idempotencyRaceHook;

    public IdempotencyTransferAttemptService(
            TransferRequestFingerprint transferRequestFingerprint,
            CurrentUserService currentUserService,
            IdempotencyRecordRepository idempotencyRecordRepository,
            TransferMapper transferMapper,
            TransferRepository transferRepository,
            TransferService transferService,
            UserRepository userRepository,
            IdempotencyRaceHook idempotencyRaceHook) {
        this.transferRequestFingerprint = transferRequestFingerprint;
        this.currentUserService = currentUserService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transferMapper = transferMapper;
        this.transferRepository = transferRepository;
        this.transferService = transferService;
        this.userRepository = userRepository;
        this.idempotencyRaceHook = idempotencyRaceHook;
    }

    @Transactional
    public TransferResponse execute(
            CreateTransferRequest request,
            String idempotencyKey
    ) {
        String fingerprint =
                transferRequestFingerprint.fingerprint(request);
        Long currentUserId = currentUserService.getCurrentUserId();
        Optional<IdempotencyRecordEntity> record = idempotencyRecordRepository
                .findByUser_IdAndIdempotencyKey(currentUserId, idempotencyKey);

        if (record.isPresent()) {
            if (!record.get().getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException("Idempotency key already used with different request");
            }
            return transferMapper.toResponse(record.get().getTransfer());
        }

        idempotencyRaceHook.afterRecordNotFound();

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
