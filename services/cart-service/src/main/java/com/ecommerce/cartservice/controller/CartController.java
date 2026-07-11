package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.dto.AddItemRequest;
import com.ecommerce.cartservice.dto.ApiResponse;
import com.ecommerce.cartservice.dto.Cart;
import com.ecommerce.cartservice.dto.UpdateItemRequest;
import com.ecommerce.cartservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<Cart>> getCart(
            @PathVariable String userId,
            Authentication authentication) {

        checkOwnership(userId, authentication);
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/{userId}/items")
    public ResponseEntity<ApiResponse<Cart>> addItem(
            @PathVariable String userId,
            @Valid @RequestBody AddItemRequest request,
            Authentication authentication) {

        checkOwnership(userId, authentication);
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    @PutMapping("/{userId}/items/{productId}")
    public ResponseEntity<ApiResponse<Cart>> updateItem(
            @PathVariable String userId,
            @PathVariable String productId,
            @Valid @RequestBody UpdateItemRequest request,
            Authentication authentication) {

        checkOwnership(userId, authentication);
        return ResponseEntity.ok(cartService.updateItem(userId, productId, request));
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public ResponseEntity<ApiResponse<Cart>> removeItem(
            @PathVariable String userId,
            @PathVariable String productId,
            Authentication authentication) {

        checkOwnership(userId, authentication);
        return ResponseEntity.ok(cartService.removeItem(userId, productId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearCart(
            @PathVariable String userId,
            Authentication authentication) {

        checkOwnership(userId, authentication);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verifies the authenticated principal owns the requested userId, unless they hold ROLE_ADMIN.
     */
    private void checkOwnership(String userId, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (!isAdmin && !userId.equals(authentication.getName())) {
            throw new AccessDeniedException("Access denied: you can only access your own cart");
        }
    }
}
