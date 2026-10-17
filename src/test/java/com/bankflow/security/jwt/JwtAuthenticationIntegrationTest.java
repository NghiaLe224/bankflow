package com.bankflow.security.jwt;

import com.bankflow.config.JwtProperties;
import com.bankflow.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class JwtAuthenticationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void protectedEndpoint_shouldReturn401_whenAuthenticationIsMissing() throws Exception {
        mockMvc.perform(get("/security/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpoint_shouldReturn200_whenAccessTokenIsValid() throws Exception {
        String token = jwtTokenService.generateAccessToken(123L, "nghia@example.com", UserRole.USER);

        mockMvc.perform(get("/security/me")
                        .header(
                                "Authorization", "Bearer " + token
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value("123"))
                .andExpect(
                        jsonPath("$.authorities[0]")
                                .value("ROLE_USER")
                );
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenUsingBasicAuthentication() throws Exception {
        mockMvc.perform(get("/security/me")
                        .with(
                                httpBasic("bankflow", "123456")
                        )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED")
                );
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenAccessTokenIsMalformed()
            throws Exception {

        mockMvc.perform(
                        get("/security/me")
                                .header(
                                        "Authorization",
                                        "Bearer not-a-valid-jwt"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenRefreshTokenIsUsedAsAccessToken()
            throws Exception {

        String refreshToken =
                jwtTokenService.generateRefreshToken(
                        123L,
                        "nghia@example.com"
                );

        mockMvc.perform(
                        get("/security/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + refreshToken
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenAccessTokenIsExpired()
            throws Exception {

        JwtProperties expiredProperties =
                new JwtProperties(
                        jwtProperties.secret(),
                        Duration.ofSeconds(-1),
                        jwtProperties.refreshTokenExpiration()
                );

        JwtTokenService expiredTokenService =
                new JwtTokenService(expiredProperties);

        String expiredToken =
                expiredTokenService.generateAccessToken(
                        123L,
                        "nghia@example.com",
                        UserRole.USER
                );

        mockMvc.perform(
                        get("/security/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + expiredToken
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void protectedEndpoint_shouldContainAdminAuthority_whenAccessTokenHasAdminRole()
            throws Exception {
        String token = jwtTokenService.generateAccessToken(999L, "admin@example.com", UserRole.ADMIN);

        mockMvc.perform(get("/security/me")
                .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("999"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_ADMIN"));
    }

    @Test
    void adminEndpoint_shouldReturn200_whenAccessTokenHasAdminRole() throws Exception {
        String token = jwtTokenService.generateAccessToken(
                999L,
                "admin@example.com",
                UserRole.ADMIN
        );

        mockMvc.perform(get("/security/admin")
                .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isOk())
                .andExpect(content().string("admin-secret"));
    }

    @Test
    void adminEndpoint_shouldReturn403_whenAccessTokenHasUserRole() throws Exception {
        String token = jwtTokenService.generateAccessToken(
                999L,
                "admin@example.com",
                UserRole.USER
        );

        mockMvc.perform(get("/security/admin")
                        .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    void adminEndpoint_shouldReturn401_whenAccessTokenIsMissing() throws Exception {
        mockMvc.perform(get("/security/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void methodAdminEndpoint_shouldReturn200_whenAccessTokenHasAdminRole() throws Exception {
        String token = jwtTokenService.generateAccessToken(
                999L,
                "admin@example.com",
                UserRole.ADMIN
        );

        mockMvc.perform(get("/security/method-admin")
                .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isOk())
                .andExpect(content().string("method-admin-secret"));
    }

    @Test
    void methodAdminEndpoint_shouldReturn403_whenAccessTokenHasUserRole() throws Exception {
        String token = jwtTokenService.generateAccessToken(
                999L,
                "admin@example.com",
                UserRole.USER
        );

        mockMvc.perform(get("/security/method-admin")
                .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }
}
