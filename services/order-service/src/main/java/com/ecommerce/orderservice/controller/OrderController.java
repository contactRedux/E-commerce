package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.*;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /v1/orders — place a new order.
     * Authenticated. Extracts the Bearer token and passes it to the service so
     * product stock can be validated via the Product Service (circuit-breaker protected).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            Authentication authentication) {

        String bearerToken = extractBearerToken(authHeader);
        OrderResponse response = orderService.placeOrder(request, bearerToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * GET /orders/{id} — retrieve a single order.
     * Authenticated. Customers see only their own orders; admins see any.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable UUID id,
            Authentication authentication) {

        String userId = authentication.getName();
        boolean isAdmin = hasAdminRole(authentication);
        OrderResponse response = orderService.getOrder(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /orders/user/{userId} — list orders for a user (paginated).
     * Authenticated. Customers see only their own orders; admins see any.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getOrdersByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        String requestingUserId = authentication.getName();
        boolean isAdmin = hasAdminRole(authentication);

        if (!isAdmin && !userId.equals(requestingUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied"));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<OrderResponse> response = orderService.getOrdersByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PUT /orders/{id}/status — update order status. ADMIN only.
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {

        OrderResponse response = orderService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * DELETE /orders/{id} — cancel an order.
     * Authenticated. Customers can cancel only their own orders; admins can cancel any.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable UUID id,
            Authentication authentication) {

        String userId = authentication.getName();
        boolean isAdmin = hasAdminRole(authentication);
        orderService.cancelOrder(id, userId, isAdmin);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null));
    }

    // -------------------------------------------------------------------------
    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
