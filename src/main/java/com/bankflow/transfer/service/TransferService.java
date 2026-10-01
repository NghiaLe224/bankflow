package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.InvalidTransferException;
import com.bankflow.common.exception.TransferNotFoundException;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.mapper.TransferMapper;
import com.bankflow.transfer.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final TransferRepository transferRepository;
    private final EntityManager entityManager;
    private final AccountRepository accountRepository;
    private final TransferMapper transferMapper;

    public TransferService(TransferRepository transferRepository,
                           EntityManager entityManager,
                           AccountRepository accountRepository, TransferMapper transferMapper) {
        this.transferRepository = transferRepository;
        this.entityManager = entityManager;
        this.accountRepository = accountRepository;
        this.transferMapper = transferMapper;
    }

    @Transactional
    public TransferResponse createTransfer(CreateTransferRequest request) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new InvalidTransferException("Source and destination accounts must be different");
        }

        AccountEntity fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.getFromAccountId()));

        AccountEntity toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.getToAccountId()));

        TransferEntity transfer = new TransferEntity(
                fromAccount,
                toAccount,
                request.getAmount(),
                request.getCurrency(),
                TransferStatus.PENDING

        );

        TransferEntity savedTransfer = transferRepository.saveAndFlush(transfer);
        entityManager.refresh(savedTransfer);

        return transferMapper.toResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse findTransferById(Long id) {
        TransferEntity transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));

        return transferMapper.toResponse(transfer);
    }

}
