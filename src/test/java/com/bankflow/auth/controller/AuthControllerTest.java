package com.bankflow.auth.controller;

import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.service.AuthService;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.findByEmail("testnguyen@gmail.com")
                .ifPresent(userRepository::delete);

        userRepository.findByEmail("nghia@example.com")
                .ifPresent(userRepository::delete);

        userRepository.findByEmail("loginuser@example.com")
                .ifPresent(userRepository::delete);
    }

    @Test
    void register_shouldReturn201_whenRequestIsValid() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyen Minh Test",
                                  "email": "testnguyen@gmail.com",
                                  "password": "12345678"
                                }
                                """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Nguyen Minh Test"))
                .andExpect(jsonPath("$.email").value("testnguyen@gmail.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_shouldReturn400_whenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "invalid-email",
                                  "password": "123"
                                }
                                """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void register_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        authService.register(
                new RegisterRequest(
                        "Existing User",
                        "nghia@example.com",
                        "12345678"
                )
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Le Trong Nghia",
                                  "email": "nghia@example.com",
                                  "password": "12345678"
                                }
                                """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    private void createLoginUser() {
        authService.register(
                new RegisterRequest(
                        "Login User",
                        "loginuser@example.com",
                        "12345678"
                )
        );
    }

    @Test
    void login_shouldReturn200_whenCredentialsAreValid() throws Exception {
        createLoginUser();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "LOGINUSER@EXAMPLE.COM",
                              "password": "12345678"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Login User"))
                .andExpect(jsonPath("$.email").value("loginuser@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void login_shouldReturn400_whenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "invalid-email",
                              "password": "123"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_shouldReturn401_whenEmailDoesNotExist() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "missing@example.com",
                              "password": "12345678"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }

    @Test
    void login_shouldReturn401_whenPasswordIsWrong() throws Exception {
        createLoginUser();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "loginuser@example.com",
                              "password": "wrong-password"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));
    }
}
