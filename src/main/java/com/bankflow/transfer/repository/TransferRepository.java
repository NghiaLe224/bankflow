package com.bankflow.transfer.repository;

import com.bankflow.transfer.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransferRepository extends JpaRepository<TransferEntity, Long>,
        JpaSpecificationExecutor<TransferEntity> {
}
