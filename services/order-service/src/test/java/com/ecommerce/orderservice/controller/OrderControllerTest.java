package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.config.SecurityConfig;
import com.ecommerce.orderservice.dto.*;
import com.ecommerce.orderservice.enums.OrderStatus;
import com.ecommerce.orderservice.exception.GlobalExceptionHandler;
import com.ecommerce.orderservice.filter.JwtAuthenticationFilter;
import com.ecommerce.orderservice.filter.RequestLoggingFilter;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestLoggingFilter.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private OrderResponse sampleResponse;
    private final UUID orderId  = UUID.randomUUID();
    private final String userId = "user-123";

    @BeforeEach
    void setUp() {
        sampleResponse = new OrderResponse(
                orderId,
                userId,
                OrderStatus.PENDING,
                new BigDecimal("20.00"),
                "123 Main St",
                List.of(new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin-1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // -------------------------------------------------------------------------
    // POST /orders
    // -------------------------------------------------------------------------

    @Test
    void postOrders_withValidJwt_returns201() throws Exception {
        PlaceOrderRequest req = new PlaceOrderRequest(
                userId,
                List.of(new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))),
                "123 Main St",
                "idem-key-1"
        );

        when(orderService.placeOrder(any(PlaceOrderRequest.class), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/v1/orders")
                        .with(authentication(customerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));
    }

    @Test
    void postOrders_withoutJwt_returns401() throws Exception {
        PlaceOrderRequest req = new PlaceOrderRequest(
                userId,
                List.of(new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))),
                "123 Main St",
                "idem-key-1"
        );

        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postOrders_withInvalidBody_returns400() throws Exception {
        // Missing required fields
        String badBody = "{\"userId\":\"\",\"items\":[],\"shippingAddress\":\"\",\"idempotencyKey\":\"\"}";

        mockMvc.perform(post("/v1/orders")
                        .with(authentication(customerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // -------------------------------------------------------------------------
    // GET /orders/{id}
    // -------------------------------------------------------------------------

    @Test
    void getOrder_withValidJwt_returns200() throws Exception {
        when(orderService.getOrder(eq(orderId), eq(userId), eq(false))).thenReturn(sampleResponse);

        mockMvc.perform(get("/v1/orders/{id}", orderId)
                        .with(authentication(customerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));
    }

    // -------------------------------------------------------------------------
    // PUT /orders/{id}/status
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_withAdminJwt_returns200() throws Exception {
        UpdateStatusRequest req = new UpdateStatusRequest(OrderStatus.CONFIRMED);
        OrderResponse confirmedResponse = new OrderResponse(
                orderId, userId, OrderStatus.CONFIRMED,
                new BigDecimal("20.00"), "123 Main St",
                List.of(), LocalDateTime.now(), LocalDateTime.now());

        when(orderService.updateStatus(eq(orderId), any(UpdateStatusRequest.class))).thenReturn(confirmedResponse);

        mockMvc.perform(put("/v1/orders/{id}/status", orderId)
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void updateStatus_withCustomerJwt_returns403() throws Exception {
        UpdateStatusRequest req = new UpdateStatusRequest(OrderStatus.CONFIRMED);

        mockMvc.perform(put("/v1/orders/{id}/status", orderId)
                        .with(authentication(customerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // DELETE /orders/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteOrder_withValidJwt_returns204() throws Exception {
        when(orderService.cancelOrder(eq(orderId), eq(userId), eq(false))).thenReturn(
                new OrderResponse(orderId, userId, OrderStatus.CANCELLED,
                        new BigDecimal("20.00"), "123 Main St",
                        List.of(), LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(delete("/v1/orders/{id}", orderId)
                        .with(authentication(customerAuth())))
                .andExpect(status().isNoContent());
    }
}
