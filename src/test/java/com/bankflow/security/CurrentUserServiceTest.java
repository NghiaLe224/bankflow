package com.bankflow.security;

import com.bankflow.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CurrentUserServiceTest {

    private final CurrentUserService currentUserService = new CurrentUserService();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasRole_shouldReturnTrue_whenAuthenticationContainsRole() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "10",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        assertTrue(
                currentUserService.hasRole(UserRole.ADMIN)
        );
    }

    @Test
    void hasRole_shouldReturnFalse_whenAuthenticationDoesNotContainRole() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "10",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        assertFalse(
                currentUserService.hasRole(UserRole.USER)
        );
    }

    @Test
    void hasRole_shouldThrowIllegalStateException_whenAuthenticationIsMissing() {
        assertThrows(
                IllegalStateException.class,
                () -> currentUserService.hasRole(UserRole.ADMIN)
        );
    }

    @Test
    void hasRole_shouldThrowIllegalArgumentException_whenRoleIsNull() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "10",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        assertThrows(
                IllegalArgumentException.class,
                () -> currentUserService.hasRole(null)
        );
    }

}
