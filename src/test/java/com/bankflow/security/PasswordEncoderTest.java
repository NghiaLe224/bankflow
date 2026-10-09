package com.bankflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordEncoderTest {

    @Test
    void passwordEncoder_shouldMatchRawPassword() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        String rawPassword = "123456";

        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        assertNotEquals(hash1, hash2);

        assertTrue(passwordEncoder.matches(rawPassword, hash1));
        assertTrue(passwordEncoder.matches(rawPassword, hash2));

        assertFalse(passwordEncoder.matches("wrong-password", hash1));
        assertFalse(passwordEncoder.matches("wrong-password", hash2));
    }
}
