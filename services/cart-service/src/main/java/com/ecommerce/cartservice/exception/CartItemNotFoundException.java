package com.ecommerce.cartservice.exception;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(String productId) {
        super("Cart item not found for productId: " + productId);
    }
}
