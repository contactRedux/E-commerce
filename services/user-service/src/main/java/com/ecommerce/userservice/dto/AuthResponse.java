package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.Role;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Full authentication response returned after login or register.
 * Contains both the access token and the refresh token.
 */
public record AuthResponse(
        String token,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static AuthResponse of(String token, String refreshToken, long expiresInMs, UserResponse user) {
        return new AuthResponse(token, refreshToken, "Bearer", expiresInMs / 1000, user);
    }

    /** Legacy factory kept for existing tests that do not pass refreshToken. */
    public static AuthResponse of(String token, long expiresInMs, UserResponse user) {
        return new AuthResponse(token, null, "Bearer", expiresInMs / 1000, user);
    }
}
