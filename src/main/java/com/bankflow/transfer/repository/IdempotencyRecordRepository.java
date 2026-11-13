package com.bankflow.transfer.repository;

import com.bankflow.transfer.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {
    Optional<IdempotencyRecordEntity> findByUser_IdAndIdempotencyKey(Long userId, String idempotencyKey);
}
