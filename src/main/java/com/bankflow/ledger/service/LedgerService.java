package com.bankflow.ledger.service;

import com.bankflow.ledger.entity.LedgerEntryEntity;
import com.bankflow.ledger.enums.LedgerEntryType;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.entity.TransferEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public List<LedgerEntryEntity> createBalancedEntries(TransferEntity transfer) {
        LedgerEntryEntity debitEntry = new LedgerEntryEntity(
                transfer,
                transfer.getFromAccount(),
                LedgerEntryType.DEBIT,
                transfer.getAmount(),
                transfer.getCurrency()
        );

        LedgerEntryEntity creditEntry = new LedgerEntryEntity(
                transfer,
                transfer.getToAccount(),
                LedgerEntryType.CREDIT,
                transfer.getAmount(),
                transfer.getCurrency()
        );

        return ledgerEntryRepository.saveAll(
                List.of(debitEntry, creditEntry)
        );
    }
}
