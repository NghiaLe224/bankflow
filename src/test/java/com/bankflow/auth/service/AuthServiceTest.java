package com.bankflow.auth.service;

import com.bankflow.auth.dto.LoginRequest;
import com.bankflow.auth.dto.LoginResponse;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.dto.RegisterResponse;
import com.bankflow.auth.exception.EmailAlreadyExistsException;
import com.bankflow.auth.exception.InvalidCredentialsException;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldSuccess_whenEmailIsValid() {
        RegisterRequest request = new RegisterRequest(
                "  Le Trong Nghia  ",
                "NGHIA@EXAMPLE.COM",
                "12345678"
        );
        when(userRepository.existsByEmail("nghia@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("12345678"))
                .thenReturn("$2a$fake-hash");
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse response = authService.register(request);

        ArgumentCaptor<UserEntity> captor =
                ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity savedUser = captor.getValue();

        assertEquals("Le Trong Nghia", response.fullName());
        assertEquals("nghia@example.com", response.email());

        assertEquals("Le Trong Nghia", savedUser.getFullName());
        assertEquals("nghia@example.com", savedUser.getEmail());
        assertEquals("$2a$fake-hash", savedUser.getPasswordHash());
        assertNotEquals("12345678", savedUser.getPasswordHash());
    }

    @Test
    void register_shouldFailed_whenEmailIsDuplicated() {
        RegisterRequest request = new RegisterRequest(
                "  Le Trong Nghia  ",
                "NGHIA@EXAMPLE.COM",
                "12345678"
        );
        when(userRepository.existsByEmail("nghia@example.com"))
                .thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.register(request));
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void login_shouldReturn200_whenEmailIsValid() {
        LoginRequest request = new LoginRequest(
                "NGHIA@EXAMPLE.COM",
                "12345678"
        );
        UserEntity user = new UserEntity(
                "Le Trong Nghia",
                "nghia@example.com",
                "$2a$fake-hash"
        );
        when(userRepository.findByEmail("nghia@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "12345678",
                "$2a$fake-hash"
        )).thenReturn(true);

        LoginResponse response = authService.login(request);

        verify(passwordEncoder).matches(
                "12345678",
                "$2a$fake-hash"
        );
        verify(passwordEncoder, never())
                .encode(anyString());
        assertEquals("Le Trong Nghia", response.fullName());
        assertEquals("nghia@example.com", response.email());
    }

    @Test
    void login_shouldReturn401_whenEmailIsNotValid() {
        LoginRequest request = new LoginRequest("missing@example.com", "12345678");
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );
        verify(passwordEncoder, never())
                .matches(anyString(), anyString());
    }

    @Test
    void login_shouldThrow_whenPasswordIsWrong() {
        LoginRequest request = new LoginRequest(
                "nghia@example.com",
                "wrong-password"
        );

        UserEntity user = new UserEntity(
                "Le Trong Nghia",
                "nghia@example.com",
                "$2a$fake-hash"
        );

        when(userRepository.findByEmail("nghia@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrong-password",
                "$2a$fake-hash"
        )).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(passwordEncoder).matches(
                "wrong-password",
                "$2a$fake-hash"
        );
    }

    @Test
    void login_shouldThrow_whenPasswordHashIsNull() {
        LoginRequest request = new LoginRequest(
                "legacy@example.com",
                "12345678"
        );

        UserEntity legacyUser = new UserEntity(
                "Legacy User",
                "legacy@example.com"
        );

        when(userRepository.findByEmail("legacy@example.com"))
                .thenReturn(Optional.of(legacyUser));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(userRepository).findByEmail("legacy@example.com");
        verifyNoInteractions(passwordEncoder);
    }

}
