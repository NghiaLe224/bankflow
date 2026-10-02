package com.bankflow.ledger.repository;

import com.bankflow.ledger.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {
    List<LedgerEntryEntity> findByTransferId(Long transferId);
}
