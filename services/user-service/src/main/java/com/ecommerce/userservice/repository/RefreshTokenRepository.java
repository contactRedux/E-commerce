package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.entity.RefreshToken;
import com.ecommerce.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    void revokeAllUserTokens(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :threshold")
    void deleteExpiredTokens(LocalDateTime threshold);
}
