package com.doceditor.authentication.services;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.doceditor.authentication.controllers.dto.AuthResponse;
import com.doceditor.authentication.controllers.dto.LoginRequest;
import com.doceditor.authentication.controllers.dto.RegisterRequest;
import com.doceditor.authentication.entity.User;
import com.doceditor.authentication.repositories.UserRepository;

import org.springframework.data.util.Pair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use: " + request.getEmail());
        }

        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                OffsetDateTime.now(),
                OffsetDateTime.now());

        userRepository.save(user);

        String accessToken = jwtService.generateToken(userId.toString());
        String rawRefreshToken = refreshTokenService.createRefreshToken(userId);

        return new AuthResponse(accessToken, rawRefreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        String accessToken = jwtService.generateToken(user.getId().toString());
        String rawRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new AuthResponse(accessToken, rawRefreshToken);
    }

    public AuthResponse refresh(String rawRefreshToken) {
        Pair<UUID, String> result = refreshTokenService.verifyAndRotate(rawRefreshToken);
        UUID userId = result.getFirst();
        String newRawRefreshToken = result.getSecond();

        String newAccessToken = jwtService.generateToken(userId.toString());

        return new AuthResponse(newAccessToken, newRawRefreshToken);
    }

    @Transactional
    public void logout(String userIdStr) {
        UUID userId = UUID.fromString(userIdStr);
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        refreshTokenService.revokeByUserId(userId);
    }
}
