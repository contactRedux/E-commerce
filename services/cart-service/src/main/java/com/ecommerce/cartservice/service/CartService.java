package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.dto.AddItemRequest;
import com.ecommerce.cartservice.dto.ApiResponse;
import com.ecommerce.cartservice.dto.Cart;
import com.ecommerce.cartservice.dto.CartItem;
import com.ecommerce.cartservice.dto.UpdateItemRequest;
import com.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.ecommerce.cartservice.repository.CartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final long cartTtlDays;

    public CartService(
            CartRepository cartRepository,
            @Value("${cart.ttl-days:7}") long cartTtlDays) {
        this.cartRepository = cartRepository;
        this.cartTtlDays = cartTtlDays;
    }

    public ApiResponse<Cart> getCart(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> emptyCart(userId));
        return ApiResponse.success(cart);
    }

    public ApiResponse<Cart> addItem(String userId, AddItemRequest req) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> emptyCart(userId));

        List<CartItem> items = cart.getItems();
        Optional<CartItem> existing = items.stream()
                .filter(i -> i.getProductId().equals(req.productId()))
                .findFirst();

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + req.quantity());
            item.recalculateSubtotal();
        } else {
            items.add(new CartItem(req.productId(), req.productName(), req.quantity(), req.unitPrice()));
        }

        cart.recalculateTotal();
        cartRepository.save(cart, cartTtlDays);
        return ApiResponse.success(cart);
    }

    public ApiResponse<Cart> updateItem(String userId, String productId, UpdateItemRequest req) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> emptyCart(userId));

        List<CartItem> items = cart.getItems();
        CartItem item = items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(productId));

        if (req.quantity() == 0) {
            items.remove(item);
        } else {
            item.setQuantity(req.quantity());
            item.recalculateSubtotal();
        }

        cart.recalculateTotal();
        cartRepository.save(cart, cartTtlDays);
        return ApiResponse.success(cart);
    }

    public ApiResponse<Cart> removeItem(String userId, String productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> emptyCart(userId));

        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        cart.recalculateTotal();
        cartRepository.save(cart, cartTtlDays);
        return ApiResponse.success(cart);
    }

    public void clearCart(String userId) {
        cartRepository.delete(userId);
    }

    private Cart emptyCart(String userId) {
        Cart cart = new Cart(userId);
        cart.setItems(new ArrayList<>());
        cart.setTotalAmount(BigDecimal.ZERO);
        return cart;
    }
}
