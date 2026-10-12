package com.bankflow.security.jwt;

import com.bankflow.auth.dto.LoginResponse;
import com.bankflow.auth.dto.RefreshTokenResponse;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.service.AuthService;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class JwtAuthFlowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        cleanUpTestUser();
    }

    @AfterEach
    void tearDown() {
        cleanUpTestUser();
    }

    private void cleanUpTestUser() {
        userRepository.findByEmail("jwtflow@example.com")
                .ifPresent(userRepository::delete);
    }

    @Test
    void login_shouldIssueAccessToken_thatCanAuthenticateProtectedEndpoint() throws Exception {
        authService.register(
                new RegisterRequest(
                        "JWT Flow User",
                        "jwtflow@example.com",
                        "12345678"
                )
        );

        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "jwtflow@example.com",
                                          "password": "12345678"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(),
                        LoginResponse.class
                );

        String accessToken = loginResponse.accessToken();
        String userId = loginResponse.id().toString();

        mockMvc.perform(
                        get("/security/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value(userId));
    }

    @Test
    void refresh_shouldIssueAccessToken_thatCanAuthenticateProtectedEndpoint()
            throws Exception {

        authService.register(
                new RegisterRequest(
                        "JWT Flow User",
                        "jwtflow@example.com",
                        "12345678"
                )
        );

        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "jwtflow@example.com",
                                      "password": "12345678"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(),
                        LoginResponse.class
                );

        String refreshToken = loginResponse.refreshToken();
        String userId = loginResponse.id().toString();

        MvcResult secondResult = mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "refreshToken": "%s"
                                    }
                                    """.formatted(refreshToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        RefreshTokenResponse refreshTokenResponse =
                objectMapper.readValue(
                        secondResult.getResponse().getContentAsString(),
                        RefreshTokenResponse.class
                );

        String newAccessToken =
                refreshTokenResponse.accessToken();

        mockMvc.perform(
                        get("/security/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + newAccessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.name").value(userId));
    }
}
