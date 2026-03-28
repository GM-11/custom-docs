package com.doceditor.authentication.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.doceditor.authentication.entity.RefreshToken;
import com.doceditor.authentication.entity.User;
import com.doceditor.authentication.repositories.RefreshTokenRepository;
import com.doceditor.authentication.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    @Value("${app.refresh-token.expiration-ms}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshTokenService(RefreshTokenRepository repo, UserRepository userRepo) {
        this.refreshTokenRepository = repo;
        this.userRepository = userRepo;
    }

    @Transactional
    public String createRefreshToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        refreshTokenRepository.deleteByUser(user);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(OffsetDateTime.now().plusNanos(refreshTokenDurationMs * 1_000_000L));
        token.setCreatedAt(OffsetDateTime.now());
        token.setRevoked(false);

        refreshTokenRepository.save(token);

        return rawToken;
    }

    @Transactional
    public Pair<UUID, String> verifyAndRotate(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (stored.isRevoked()) {
            refreshTokenRepository.delete(stored);
            throw new RuntimeException("Refresh token has been revoked. Please log in again.");
        }

        if (isTokenExpired(stored)) {
            refreshTokenRepository.delete(stored);
            throw new RuntimeException("Refresh token has expired. Please log in again.");
        }

        UUID userId = stored.getUser().getId();

        refreshTokenRepository.delete(stored);

        String newRawToken = createRefreshToken(userId);
        return Pair.of(userId, newRawToken);
    }

    @Transactional
    public void revokeByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        refreshTokenRepository.findByUser(user).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public boolean isTokenExpired(RefreshToken token) {
        return token.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
