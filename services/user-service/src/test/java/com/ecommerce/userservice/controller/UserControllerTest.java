package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.config.SecurityConfig;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.exception.GlobalExceptionHandler;
import com.ecommerce.userservice.filter.JwtAuthenticationFilter;
import com.ecommerce.userservice.service.JwtService;
import com.ecommerce.userservice.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String VALID_TOKEN = "valid.jwt.token";

    private UserResponse sampleUserResponse() {
        return new UserResponse(
                USER_ID,
                "alice@example.com",
                "Alice", "Smith",
                Role.ROLE_CUSTOMER,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @BeforeEach
    void setUp() {
        Claims claims = new DefaultClaims(Map.of(
                "sub", USER_ID.toString(),
                "email", "alice@example.com",
                "role", "ROLE_CUSTOMER"
        ));
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(claims);
    }

    @Test
    void getUser_withValidJwt_returns200() throws Exception {
        when(userService.getUser(eq(USER_ID), any(Authentication.class)))
                .thenReturn(sampleUserResponse());

        mockMvc.perform(get("/users/{id}", USER_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void getUser_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/users/{id}", USER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllUsers_withCustomerRole_returns403() throws Exception {
        when(userService.getAllUsers(any(Pageable.class), any(Authentication.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Admin access required"));

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getAllUsers_withAdminRole_returns200() throws Exception {
        Claims adminClaims = new DefaultClaims(Map.of(
                "sub", USER_ID.toString(),
                "email", "admin@example.com",
                "role", "ROLE_ADMIN"
        ));
        when(jwtService.validateToken("admin.token")).thenReturn(adminClaims);

        Page<UserResponse> page = new PageImpl<>(List.of(sampleUserResponse()));
        when(userService.getAllUsers(any(Pageable.class), any(Authentication.class))).thenReturn(page);

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
