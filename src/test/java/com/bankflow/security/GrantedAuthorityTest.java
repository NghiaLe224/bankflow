package com.bankflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GrantedAuthorityTest {

    @Test
    void authentication_shouldContainUserRoleAuthority() {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "10",
                null,
                List.of(authority)
        );

        assertTrue(authentication.isAuthenticated());
        assertEquals("10", authentication.getName());
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(a -> "ROLE_USER".equals(a.getAuthority()))
        );
    }

    @Test
    void usernamePasswordAuthenticationToken_shouldRepresentDifferentAuthenticationStates() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken(
                        "10",
                        "raw-password"
                );

        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_USER");

        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(
                        "10",
                        null,
                        List.of(authority)
                );

        assertFalse(unauthenticated.isAuthenticated());
        assertTrue(unauthenticated.getAuthorities().isEmpty());
        assertTrue(authenticated.isAuthenticated());
        assertTrue(authenticated.getAuthorities()
                .stream()
                .anyMatch(auth -> "ROLE_USER".equals(auth.getAuthority())));

    }
}
