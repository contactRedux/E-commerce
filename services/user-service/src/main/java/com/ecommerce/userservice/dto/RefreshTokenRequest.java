package com.ecommerce.userservice.dto;

/**
 * Request body for token refresh and logout endpoints.
 *
 * @param refreshToken the opaque refresh token string
 */
public record RefreshTokenRequest(String refreshToken) {}
