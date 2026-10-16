package com.bankflow.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig(MethodSecurityTest.TestConfig.class)
public class MethodSecurityTest {

    @Autowired
    private AdminOnlyService adminOnlyService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        AdminOnlyService adminOnlyService() {
            return new AdminOnlyService();
        }
    }

    static class AdminOnlyService {

        @PreAuthorize("hasRole('ADMIN')")
        public String getSecret() {
            return "admin-secret";
        }
    }

    @Test
    void adminOnlyMethod_shouldAllowAdminRole() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "10",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String result = adminOnlyService.getSecret();

        assertEquals("admin-secret", result);
    }

    @Test
    void adminOnlyMethod_shouldDenyUserRole() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "10",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(AccessDeniedException.class,
                () -> adminOnlyService.getSecret());
    }


}
