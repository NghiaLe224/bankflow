package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.dto.PageResponse;
import com.bankflow.common.exception.*;
import com.bankflow.ledger.service.LedgerService;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.mapper.TransferMapper;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.transfer.specification.TransferSpecification;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TransferService {
    private final TransferRepository transferRepository;
    private final EntityManager entityManager;
    private final AccountRepository accountRepository;
    private final TransferMapper transferMapper;
    private final LedgerService ledgerService;

    public TransferService(TransferRepository transferRepository,
                           EntityManager entityManager,
                           AccountRepository accountRepository,
                           TransferMapper transferMapper,
                           LedgerService ledgerService
    ) {
        this.transferRepository = transferRepository;
        this.entityManager = entityManager;
        this.accountRepository = accountRepository;
        this.transferMapper = transferMapper;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public TransferResponse createTransfer(CreateTransferRequest request) {
        if (request.amount() == null
                || request.amount().signum() <= 0) {
            throw new InvalidTransferException(
                    "Transfer amount must be greater than zero"
            );
        }

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidTransferException("Source and destination accounts must be different");
        }

        AccountEntity fromAccount = accountRepository.findById(request.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.fromAccountId()));

        AccountEntity toAccount = accountRepository.findById(request.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.toAccountId()));

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(fromAccount.getId());
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException(toAccount.getId());
        }

        TransferEntity transfer = new TransferEntity(
                fromAccount,
                toAccount,
                request.amount(),
                request.currency(),
                TransferStatus.PENDING
        );

        TransferEntity savedTransfer = transferRepository.saveAndFlush(transfer);

        fromAccount.debit(savedTransfer.getAmount());
        toAccount.credit(savedTransfer.getAmount());

        ledgerService.createBalancedEntries(savedTransfer);
        savedTransfer.markCompleted();

        entityManager.flush();
        entityManager.refresh(savedTransfer);

        return transferMapper.toResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse findTransferById(Long id) {
        TransferEntity transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));

        return transferMapper.toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> getTransferHistory(
            int page,
            int size,
            TransferStatus status,
            Long accountId,
            LocalDateTime from,
            LocalDateTime to) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransferFilterException(
                    "from must be before or equal to to"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.Direction.DESC,
                "createdAt",
                "id"
        );

        Specification<TransferEntity> spec = Specification.unrestricted();

        if (status != null) {
            spec = spec.and(TransferSpecification.hasStatus(status));
        }

        if (accountId != null) {
            spec = spec.and(TransferSpecification.hasAccount(accountId));
        }

        if (from != null) {
            spec = spec.and(TransferSpecification.createdAtFrom(from));
        }

        if (to != null) {
            spec = spec.and(TransferSpecification.createdAtTo(to));
        }

        Page<TransferEntity> transfers = transferRepository.findAll(spec, pageable);

        return PageResponse.from(transfers.map(transferMapper::toResponse));
    }

}
