package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.config.SecurityConfig;
import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.RefreshTokenRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.exception.GlobalExceptionHandler;
import com.ecommerce.userservice.filter.JwtAuthenticationFilter;
import com.ecommerce.userservice.service.AuthService;
import com.ecommerce.userservice.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link AuthController}.
 *
 * <p>All service dependencies are mocked. Tests cover the happy-path HTTP
 * contract (status codes, JSON envelope) as well as validation error paths.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private UserResponse sampleUserResponse() {
        return new UserResponse(
                UUID.randomUUID(),
                "alice@example.com",
                "Alice", "Smith",
                Role.ROLE_CUSTOMER,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private AuthResponse sampleAuthResponse(String token, String refreshToken) {
        return AuthResponse.of(token, refreshToken, 86400000L, sampleUserResponse());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /v1/auth/register
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void register_returns201WithTokenAndRefreshToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(sampleAuthResponse("jwt.token.here", "refresh-token-abc"));

        RegisterRequest body = new RegisterRequest("alice@example.com", "password123", "Alice", "Smith");

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-abc"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest body = new RegisterRequest("not-an-email", "password123", "Alice", null);

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /v1/auth/login
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void login_returns200WithTokenAndRefreshToken() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(sampleAuthResponse("jwt.token.login", "refresh-token-login"));

        LoginRequest body = new LoginRequest("alice@example.com", "password123");

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.token").value("jwt.token.login"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-login"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /v1/auth/refresh
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void refresh_validRefreshToken_returns200WithNewTokenPair() throws Exception {
        when(authService.refresh(eq("valid-refresh-token")))
                .thenReturn(sampleAuthResponse("new.access.token", "new-refresh-token"));

        RefreshTokenRequest body = new RefreshTokenRequest("valid-refresh-token");

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.token").value("new.access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refresh_invalidRefreshToken_returns400() throws Exception {
        when(authService.refresh(eq("bad-token")))
                .thenThrow(new IllegalArgumentException("Refresh token has been revoked"));

        RefreshTokenRequest body = new RefreshTokenRequest("bad-token");

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /v1/auth/logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void logout_validRefreshToken_returns204() throws Exception {
        doNothing().when(authService).logout(eq("valid-refresh-token"));

        RefreshTokenRequest body = new RefreshTokenRequest("valid-refresh-token");

        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /v1/auth/public-key
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPublicKey_returns200WithPem() throws Exception {
        when(jwtService.getPublicKeyPem())
                .thenReturn("-----BEGIN PUBLIC KEY-----\nMIIB...\n-----END PUBLIC KEY-----");

        mockMvc.perform(get("/v1/auth/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").value(
                        org.hamcrest.Matchers.containsString("BEGIN PUBLIC KEY")));
    }
}
