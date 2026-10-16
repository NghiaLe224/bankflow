package com.bankflow.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public Long getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new IllegalStateException(
                    "Authenticated user is required"
            );
        }

        String userId = authentication.getName();

        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException(
                    "Invalid authenticated user id"
            );
        }

        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid authenticated user id"
            );
        }
    }
}
