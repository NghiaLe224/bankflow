package com.bankflow.security.jwt;

import com.bankflow.config.JwtProperties;
import com.bankflow.security.jwt.exception.InvalidJwtTokenException;
import com.bankflow.security.jwt.exception.JwtTokenExpiredException;
import com.bankflow.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenServiceTest {

    JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder()
                .encodeToString(
                        "01234567890123456789012345678901"
                                .getBytes(StandardCharsets.UTF_8)
                );

        JwtProperties properties = new JwtProperties(
                secret,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );

        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void accessToken_shouldContainExpectedClaims() {
        String token = jwtTokenService.generateAccessToken(123L, "nghia@example.com", UserRole.USER);
        Claims claims = jwtTokenService.parseAccessToken(token);

        assertEquals("123", claims.getSubject());
        assertEquals("nghia@example.com", claims.get("email", String.class));
        assertEquals("access", claims.get("token_type", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertEquals(
                "USER",
                claims.get("role", String.class)
        );
    }

    @Test
    void refreshToken_shouldContainExpectedClaims() {
        String token =
                jwtTokenService.generateRefreshToken(
                        123L,
                        "nghia@example.com"
                );

        Claims claims =
                jwtTokenService.parseRefreshToken(token);

        assertEquals("123", claims.getSubject());
        assertEquals(
                "refresh",
                claims.get("token_type", String.class)
        );
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void parseAccessToken_shouldFail_whenTokenIsRefreshToken() {
        String refreshToken =
                jwtTokenService.generateRefreshToken(
                        123L,
                        "nghia@example.com"
                );

        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtTokenService.parseAccessToken(refreshToken)
        );
    }

    @Test
    void parseAccessToken_shouldFail_whenPayloadIsTampered() {
        String token = jwtTokenService.generateAccessToken(
                123L,
                "nghia@example.com",
                UserRole.USER
        );

        String[] parts = token.split("\\.");

        String fakePayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        """
                                {
                                  "sub": "999",
                                  "email": "attacker@example.com",
                                  "token_type": "access"
                                }
                                """.getBytes(StandardCharsets.UTF_8)
                );

        String tamperedToken =
                parts[0] + "." +
                        fakePayload + "." +
                        parts[2];

        assertThrows(
                InvalidJwtTokenException.class,
                () -> jwtTokenService.parseAccessToken(tamperedToken)
        );
    }

    @Test
    void parseAccessToken_shouldFail_whenTokenIsExpired() {
        String secret = Base64.getEncoder()
                .encodeToString(
                        "01234567890123456789012345678901"
                                .getBytes(StandardCharsets.UTF_8)
                );

        JwtProperties expiredProperties =
                new JwtProperties(
                        secret,
                        Duration.ofSeconds(-1),
                        Duration.ofDays(7)
                );

        JwtTokenService expiredJwtTokenService =
                new JwtTokenService(expiredProperties);

        String expiredToken =
                expiredJwtTokenService.generateAccessToken(
                        123L,
                        "nghia@example.com",
                        UserRole.USER
                );

        assertThrows(
                JwtTokenExpiredException.class,
                () -> expiredJwtTokenService.parseAccessToken(expiredToken)
        );
    }


}
