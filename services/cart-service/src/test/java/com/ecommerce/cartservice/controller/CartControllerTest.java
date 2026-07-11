package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.config.SecurityConfig;
import com.ecommerce.cartservice.dto.AddItemRequest;
import com.ecommerce.cartservice.dto.ApiResponse;
import com.ecommerce.cartservice.dto.Cart;
import com.ecommerce.cartservice.exception.GlobalExceptionHandler;
import com.ecommerce.cartservice.filter.JwtAuthenticationFilter;
import com.ecommerce.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    private Cart emptyCartFor(String userId) {
        Cart cart = new Cart(userId);
        return cart;
    }

    // ---- GET /cart/{userId} ----

    @Test
    @WithMockUser(username = "user-123", authorities = {"ROLE_CUSTOMER"})
    void getCart_authenticatedOwner_returns200() throws Exception {
        when(cartService.getCart("user-123")).thenReturn(ApiResponse.success(emptyCartFor("user-123")));

        mockMvc.perform(get("/cart/user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.userId").value("user-123"));
    }

    @Test
    void getCart_noAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/cart/user-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "other-user", authorities = {"ROLE_CUSTOMER"})
    void getCart_differentUser_returns403() throws Exception {
        mockMvc.perform(get("/cart/user-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin-1", authorities = {"ROLE_ADMIN"})
    void getCart_adminBypassesOwnershipCheck_returns200() throws Exception {
        when(cartService.getCart("user-123")).thenReturn(ApiResponse.success(emptyCartFor("user-123")));

        mockMvc.perform(get("/cart/user-123"))
                .andExpect(status().isOk());
    }

    // ---- POST /cart/{userId}/items ----

    @Test
    @WithMockUser(username = "user-123", authorities = {"ROLE_CUSTOMER"})
    void addItem_validRequest_returns200() throws Exception {
        AddItemRequest req = new AddItemRequest("prod-1", "Widget", 2, new BigDecimal("9.99"));
        Cart cart = emptyCartFor("user-123");
        when(cartService.addItem(eq("user-123"), any(AddItemRequest.class)))
                .thenReturn(ApiResponse.success(cart));

        mockMvc.perform(post("/cart/user-123/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @WithMockUser(username = "user-123", authorities = {"ROLE_CUSTOMER"})
    void addItem_missingProductId_returns400() throws Exception {
        // productId is blank → validation error
        String body = "{\"productId\":\"\",\"productName\":\"Widget\",\"quantity\":1,\"unitPrice\":9.99}";

        mockMvc.perform(post("/cart/user-123/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @WithMockUser(username = "user-123", authorities = {"ROLE_CUSTOMER"})
    void addItem_zeroQuantity_returns400() throws Exception {
        String body = "{\"productId\":\"prod-1\",\"productName\":\"Widget\",\"quantity\":0,\"unitPrice\":9.99}";

        mockMvc.perform(post("/cart/user-123/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ---- DELETE /cart/{userId} ----

    @Test
    @WithMockUser(username = "user-123", authorities = {"ROLE_CUSTOMER"})
    void clearCart_authenticatedOwner_returns204() throws Exception {
        mockMvc.perform(delete("/cart/user-123"))
                .andExpect(status().isNoContent());

        verify(cartService).clearCart("user-123");
    }
}
