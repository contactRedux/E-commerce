package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.config.SecurityConfig;
import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private UserResponse sampleUserResponse() {
        return new UserResponse(
                UUID.randomUUID(),
                "alice@example.com",
                "Alice", "Smith",
                Role.ROLE_CUSTOMER,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void register_returns201WithToken() throws Exception {
        AuthResponse authResponse = AuthResponse.of("jwt.token.here", 86400000L, sampleUserResponse());
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        RegisterRequest body = new RegisterRequest("alice@example.com", "password123", "Alice", "Smith");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_returns200WithToken() throws Exception {
        AuthResponse authResponse = AuthResponse.of("jwt.token.login", 86400000L, sampleUserResponse());
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        LoginRequest body = new LoginRequest("alice@example.com", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.token").value("jwt.token.login"));
    }

    @Test
    void getPublicKey_returns200WithPem() throws Exception {
        when(jwtService.getPublicKeyPem()).thenReturn("-----BEGIN PUBLIC KEY-----\nMIIB...\n-----END PUBLIC KEY-----");

        mockMvc.perform(get("/auth/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("BEGIN PUBLIC KEY")));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest body = new RegisterRequest("not-an-email", "password123", "Alice", null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
