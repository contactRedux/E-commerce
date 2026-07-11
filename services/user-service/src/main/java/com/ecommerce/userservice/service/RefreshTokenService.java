package com.ecommerce.userservice.service;

import com.ecommerce.userservice.entity.RefreshToken;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Manages the lifecycle of refresh tokens.
 *
 * <p>Refresh tokens are long-lived, randomly generated strings stored in the
 * database. On each use the old token is revoked and a new one is issued
 * (rotation), preventing reuse after a successful refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Creates a new refresh token for the given user, revoking all previous
     * active tokens for that user first (single active session policy).
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Revoke all existing active tokens for this user
        refreshTokenRepository.revokeAllUserTokens(user);

        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        String tokenValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token);
    }

    /**
     * Validates a refresh token string, returning the associated {@link RefreshToken}
     * entity if valid.
     *
     * @param tokenValue the raw token string from the request
     * @return the valid RefreshToken
     * @throws IllegalArgumentException if the token is not found, revoked, or expired
     */
    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (!token.isValid()) {
            throw new IllegalArgumentException(
                    token.isRevoked() ? "Refresh token has been revoked" : "Refresh token has expired");
        }
        return token;
    }

    /**
     * Revokes a specific token (used on logout).
     */
    @Transactional
    public void revokeToken(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
            log.info("Refresh token revoked for userId={}", t.getUser().getId());
        });
    }

    /**
     * Scheduled cleanup — purges expired tokens daily to keep the table lean.
     * Runs at 03:00 UTC every day.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Expired refresh tokens purged");
    }
}
