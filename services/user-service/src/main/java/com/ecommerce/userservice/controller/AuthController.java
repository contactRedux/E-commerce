package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.ApiResponse;
import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.RefreshTokenRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.service.AuthService;
import com.ecommerce.userservice.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints. All routes here are public (no JWT required).
 *
 * <ul>
 *   <li>{@code POST /v1/auth/register} — create account, get access + refresh tokens</li>
 *   <li>{@code POST /v1/auth/login}    — exchange credentials for access + refresh tokens</li>
 *   <li>{@code POST /v1/auth/refresh}  — exchange a valid refresh token for a new token pair</li>
 *   <li>{@code POST /v1/auth/logout}   — revoke the supplied refresh token</li>
 *   <li>{@code GET  /v1/auth/public-key} — expose the RSA public key (used by gateway/services)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request), "User registered successfully");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Login successful");
    }

    /**
     * Issues a new access token and rotated refresh token.
     * The supplied refresh token is invalidated (one-time use).
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()), "Token refreshed");
    }

    /**
     * Revokes the supplied refresh token (stateless logout).
     * The client should also discard the access token locally.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/public-key")
    public ApiResponse<String> getPublicKey() {
        return ApiResponse.success(jwtService.getPublicKeyPem(), "RSA public key for JWT verification");
    }
}
