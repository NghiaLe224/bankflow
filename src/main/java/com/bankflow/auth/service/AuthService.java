package com.bankflow.auth.service;

import com.bankflow.auth.dto.LoginRequest;
import com.bankflow.auth.dto.LoginResponse;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.dto.RegisterResponse;
import com.bankflow.auth.exception.EmailAlreadyExistsException;
import com.bankflow.auth.exception.InvalidCredentialsException;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        return new LoginResponse(user.getId(), user.getFullName(), user.getEmail());
    }
}
