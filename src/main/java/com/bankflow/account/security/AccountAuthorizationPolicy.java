package com.bankflow.account.security;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.security.CurrentUserService;
import com.bankflow.security.exception.ForbiddenOperationException;
import com.bankflow.user.enums.UserRole;
import org.springframework.stereotype.Component;

@Component
public class AccountAuthorizationPolicy {

    private final CurrentUserService currentUserService;

    public AccountAuthorizationPolicy(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public void assertCanAccess(AccountEntity account) {

        Long currentUserId = currentUserService.getCurrentUserId();
        Long ownerId = account.getUser().getId();

        if (!currentUserId.equals(ownerId) && !currentUserService.hasRole(UserRole.ADMIN)) {
            throw new ForbiddenOperationException();
        }

    }
}
