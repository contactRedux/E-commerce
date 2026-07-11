package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.*;
import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderStateException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private OrderService orderService;

    private Order sampleOrder;
    private final UUID orderId   = UUID.randomUUID();
    private final String userId  = "user-123";

    @BeforeEach
    void setUp() {
        OrderItem item = new OrderItem();
        item.setProductId("prod-1");
        item.setProductName("Widget");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setSubtotal(new BigDecimal("20.00"));

        sampleOrder = new Order();
        sampleOrder.setId(orderId);
        sampleOrder.setUserId(userId);
        sampleOrder.setIdempotencyKey("idem-key-1");
        sampleOrder.setStatus(OrderStatus.PENDING);
        sampleOrder.setTotalAmount(new BigDecimal("20.00"));
        sampleOrder.setShippingAddress("123 Main St");
        sampleOrder.setItems(List.of(item));
        sampleOrder.setCreatedAt(LocalDateTime.now());
        sampleOrder.setUpdatedAt(LocalDateTime.now());

        item.setOrder(sampleOrder);
    }

    // -------------------------------------------------------------------------
    // placeOrder
    // -------------------------------------------------------------------------

    @Test
    void placeOrder_success_savesAndPublishesEvent() {
        PlaceOrderRequest req = new PlaceOrderRequest(
                userId,
                List.of(new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))),
                "123 Main St",
                "idem-key-1"
        );

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        OrderResponse response = orderService.placeOrder(req);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(any(Order.class));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafkaProducerService).publishOrderPlaced(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId.toString());
    }

    @Test
    void placeOrder_idempotent_returnsExistingOrderWithoutSaving() {
        PlaceOrderRequest req = new PlaceOrderRequest(
                userId,
                List.of(new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))),
                "123 Main St",
                "idem-key-1"
        );

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(sampleOrder));

        OrderResponse response = orderService.placeOrder(req);

        assertThat(response.id()).isEqualTo(orderId);
        verify(orderRepository, never()).save(any());
        verify(kafkaProducerService, never()).publishOrderPlaced(any());
    }

    // -------------------------------------------------------------------------
    // getOrder
    // -------------------------------------------------------------------------

    @Test
    void getOrder_asOwner_returnsOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        OrderResponse response = orderService.getOrder(orderId, userId, false);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void getOrder_asAdmin_returnsOrderForDifferentUser() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        OrderResponse response = orderService.getOrder(orderId, "other-user", true);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void getOrder_accessDenied_throwsAccessDeniedException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.getOrder(orderId, "other-user", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getOrder_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId, userId, false))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // updateStatus
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_validTransition_pendingToConfirmed() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(orderId, new UpdateStatusRequest(OrderStatus.CONFIRMED));

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(kafkaProducerService).publishOrderStatusUpdated(any(OrderStatusUpdatedEvent.class));
    }

    @Test
    void updateStatus_invalidTransition_deliveredToPending_throwsInvalidOrderStateException() {
        sampleOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateStatusRequest(OrderStatus.PENDING)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Invalid status transition");
    }

    // -------------------------------------------------------------------------
    // cancelOrder
    // -------------------------------------------------------------------------

    @Test
    void cancelOrder_fromPending_cancelsAndPublishesEvent() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId, userId, false);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(kafkaProducerService).publishOrderStatusUpdated(any(OrderStatusUpdatedEvent.class));
    }

    @Test
    void cancelOrder_fromShipped_throwsInvalidOrderStateException() {
        sampleOrder.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId, false))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot cancel order in status");
    }
}
