package com.bankflow.transfer.specification;

import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.TransferStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TransferSpecification {
    public static Specification<TransferEntity> hasStatus(TransferStatus status) {
        return (root, query, cb) -> cb.equal(
                root.get("status"),
                status
        );
    }

    public static Specification<TransferEntity> hasAccount(Long accountId) {
        return (root, query, criteriaBuilder) -> {
            var fromPredicate = criteriaBuilder.equal(root.get("fromAccount").get("id"), accountId);
            var toPredicate = criteriaBuilder.equal(root.get("toAccount").get("id"), accountId);
            return criteriaBuilder.or(fromPredicate, toPredicate);
        };
    }

    public static Specification<TransferEntity> createdAtFrom(LocalDateTime from) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<TransferEntity> createdAtTo(LocalDateTime to) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<TransferEntity> involvesUser(Long userId) {
        return (root, query, criteriaBuilder) -> {
            var fromUser = criteriaBuilder.equal(root.get("fromAccount").get("user").get("id"), userId);
            var toUser = criteriaBuilder.equal(root.get("toAccount").get("user").get("id"), userId);
            return criteriaBuilder.or(fromUser, toUser);
        };
    }
}
