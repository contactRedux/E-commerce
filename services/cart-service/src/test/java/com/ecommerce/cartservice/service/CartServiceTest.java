package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.dto.AddItemRequest;
import com.ecommerce.cartservice.dto.Cart;
import com.ecommerce.cartservice.dto.CartItem;
import com.ecommerce.cartservice.dto.UpdateItemRequest;
import com.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.ecommerce.cartservice.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    private CartService cartService;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, 7L);
    }

    // ---- getCart ----

    @Test
    void getCart_existingCart() {
        Cart cart = cartWithOneItem();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        var response = cartService.getCart(USER_ID);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data()).isSameAs(cart);
    }

    @Test
    void getCart_noCart_returnsEmptyCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        var response = cartService.getCart(USER_ID);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.data().getUserId()).isEqualTo(USER_ID);
        assertThat(response.data().getItems()).isEmpty();
        assertThat(response.data().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- addItem ----

    @Test
    void addItem_newItem() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        AddItemRequest req = new AddItemRequest("prod-1", "Widget", 2, new BigDecimal("9.99"));
        var response = cartService.addItem(USER_ID, req);

        Cart cart = response.data();
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductId()).isEqualTo("prod-1");
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(cart.getItems().get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("19.98"));
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(new BigDecimal("19.98"));
        verify(cartRepository).save(any(Cart.class), eq(7L));
    }

    @Test
    void addItem_existingItem_incrementsQuantity() {
        Cart existing = new Cart(USER_ID);
        CartItem item = new CartItem("prod-1", "Widget", 1, new BigDecimal("9.99"));
        existing.setItems(new ArrayList<>(List.of(item)));
        existing.recalculateTotal();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        AddItemRequest req = new AddItemRequest("prod-1", "Widget", 3, new BigDecimal("9.99"));
        var response = cartService.addItem(USER_ID, req);

        Cart cart = response.data();
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(4);
        assertThat(cart.getItems().get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("39.96"));
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(new BigDecimal("39.96"));
    }

    @Test
    void addItem_differentItem_addsTwoItems() {
        Cart existing = new Cart(USER_ID);
        CartItem item = new CartItem("prod-1", "Widget", 1, new BigDecimal("9.99"));
        existing.setItems(new ArrayList<>(List.of(item)));
        existing.recalculateTotal();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        AddItemRequest req = new AddItemRequest("prod-2", "Gadget", 1, new BigDecimal("4.99"));
        var response = cartService.addItem(USER_ID, req);

        Cart cart = response.data();
        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getTotalAmount()).isEqualByComparingTo(new BigDecimal("14.98"));
    }

    // ---- updateItem ----

    @Test
    void updateItem_success() {
        Cart cart = cartWithOneItem();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        UpdateItemRequest req = new UpdateItemRequest(5);
        var response = cartService.updateItem(USER_ID, "prod-1", req);

        CartItem updated = response.data().getItems().get(0);
        assertThat(updated.getQuantity()).isEqualTo(5);
        assertThat(updated.getSubtotal()).isEqualByComparingTo(new BigDecimal("49.95"));
        assertThat(response.data().getTotalAmount()).isEqualByComparingTo(new BigDecimal("49.95"));
        verify(cartRepository).save(any(Cart.class), eq(7L));
    }

    @Test
    void updateItem_quantityZero_removesItem() {
        Cart cart = cartWithOneItem();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        UpdateItemRequest req = new UpdateItemRequest(0);
        var response = cartService.updateItem(USER_ID, "prod-1", req);

        assertThat(response.data().getItems()).isEmpty();
        assertThat(response.data().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void updateItem_notFound_throwsCartItemNotFoundException() {
        Cart cart = cartWithOneItem();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, "prod-does-not-exist", new UpdateItemRequest(2)))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessageContaining("prod-does-not-exist");
    }

    // ---- removeItem ----

    @Test
    void removeItem_success() {
        Cart cart = cartWithOneItem();
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        var response = cartService.removeItem(USER_ID, "prod-1");

        assertThat(response.data().getItems()).isEmpty();
        assertThat(response.data().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(cartRepository).save(any(Cart.class), eq(7L));
    }

    // ---- clearCart ----

    @Test
    void clearCart_success() {
        cartService.clearCart(USER_ID);
        verify(cartRepository).delete(USER_ID);
    }

    // ---- helpers ----

    private Cart cartWithOneItem() {
        Cart cart = new Cart(USER_ID);
        CartItem item = new CartItem("prod-1", "Widget", 2, new BigDecimal("9.99"));
        cart.setItems(new ArrayList<>(List.of(item)));
        cart.recalculateTotal();
        return cart;
    }
}
