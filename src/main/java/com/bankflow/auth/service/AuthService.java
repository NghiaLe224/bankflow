package com.bankflow.auth.service;

import com.bankflow.auth.dto.*;
import com.bankflow.auth.exception.EmailAlreadyExistsException;
import com.bankflow.auth.exception.InvalidCredentialsException;
import com.bankflow.security.jwt.JwtTokenService;
import com.bankflow.security.jwt.exception.InvalidJwtTokenException;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }
        String hashedPassword = passwordEncoder.encode(request.password());

        UserEntity savedUser = userRepository.save(
                new UserEntity(request.fullName().trim(), normalizedEmail, hashedPassword)
        );

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId(), user.getEmail());
        return new LoginResponse(user.getId(), user.getFullName(), user.getEmail(), accessToken, refreshToken, "Bearer");
    }

    @Transactional(readOnly = true)
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtTokenService.parseRefreshToken(request.refreshToken());
        Long id;
        try {
            id = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidJwtTokenException();
        }

        UserEntity user = userRepository.findById(id)
                .orElseThrow(InvalidJwtTokenException::new);

        String newAccessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());

        return new RefreshTokenResponse(newAccessToken, "Bearer");
    }
}
