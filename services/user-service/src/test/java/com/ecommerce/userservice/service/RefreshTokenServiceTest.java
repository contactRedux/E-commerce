package com.ecommerce.userservice.service;

import com.ecommerce.userservice.entity.RefreshToken;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenService}.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Inject the expiry days value (normally bound from application.yml)
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiryDays", 7);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hash")
                .firstName("Alice")
                .role(Role.ROLE_CUSTOMER)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createRefreshToken
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createRefreshToken_revokesExistingAndSavesNew() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RefreshToken result = refreshTokenService.createRefreshToken(testUser);

        verify(refreshTokenRepository).revokeAllUserTokens(testUser);
        verify(refreshTokenRepository).save(captor.capture());

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.isRevoked()).isFalse();
        assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(captor.getValue().getUser()).isEqualTo(testUser);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateRefreshToken
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void validateRefreshToken_validToken_returnsToken() {
        RefreshToken token = buildToken(false, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.validateRefreshToken("valid-token");

        assertThat(result).isEqualTo(token);
    }

    @Test
    void validateRefreshToken_revokedToken_throwsIllegalArgumentException() {
        RefreshToken token = buildToken(true, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("revoked-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void validateRefreshToken_expiredToken_throwsIllegalArgumentException() {
        RefreshToken token = buildToken(false, LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // purgeExpiredTokens
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void purgeExpiredTokens_callsDeleteExpiredTokens() {
        refreshTokenService.purgeExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredTokens(any(LocalDateTime.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private RefreshToken buildToken(boolean revoked, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .token(revoked ? "revoked-token" : "valid-token")
                .revoked(revoked)
                .expiresAt(expiresAt)
                .build();
    }
}
