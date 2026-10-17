package com.bankflow.transfer.security;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.security.CurrentUserService;
import com.bankflow.security.exception.ForbiddenOperationException;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.user.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class TransferAuthorizationPolicy {
    private final CurrentUserService currentUserService;

    public TransferAuthorizationPolicy(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public void assertCanRead(TransferEntity transfer) {
        Long currentUserId = currentUserService.getCurrentUserId();
        boolean sender = currentUserId.equals(
                transfer.getFromAccount().getUser().getId()
        );
        boolean recipient = currentUserId.equals(
                transfer.getToAccount().getUser().getId()
        );
        boolean participant = sender || recipient;

        if (!participant && !currentUserService.hasRole(UserRole.ADMIN)) {
            throw new ForbiddenOperationException();
        }
    }

    public void assertCanCreateFrom(AccountEntity account) {
        Long currentUserId = currentUserService.getCurrentUserId();

        if (!currentUserId.equals(account.getUser().getId())) {
            throw new ForbiddenOperationException();
        }
    }

    public boolean canViewAllHistory() {
        return currentUserService.hasRole(UserRole.ADMIN);
    }
}
