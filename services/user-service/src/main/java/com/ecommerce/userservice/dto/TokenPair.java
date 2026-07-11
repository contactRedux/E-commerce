package com.ecommerce.userservice.dto;

/**
 * Wrapper returned by both the login and refresh endpoints.
 * Contains the short-lived access JWT and the long-lived opaque refresh token.
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenPair of(String accessToken, String refreshToken, long expiresInMs) {
        return new TokenPair(accessToken, refreshToken, "Bearer", expiresInMs / 1000);
    }
}
