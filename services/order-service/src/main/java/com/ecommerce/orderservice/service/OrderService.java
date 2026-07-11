package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.*;
import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderStateException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ProductValidationService productValidationService;

    /**
     * Places a new order.
     *
     * @param req         the order placement request
     * @param bearerToken JWT token forwarded from the HTTP request (used for
     *                    inter-service product validation via the gateway)
     */
    public OrderResponse placeOrder(PlaceOrderRequest req, String bearerToken) {
        // Idempotency: return existing order if same key already exists
        Optional<Order> existing = orderRepository.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent order request for key={}", req.idempotencyKey());
            return mapToResponse(existing.get());
        }

        // Validate stock via Product Service (circuit-breaker protected — falls back to true)
        if (bearerToken != null && !bearerToken.isBlank()) {
            boolean valid = productValidationService.validateProductsAndStock(req.items(), bearerToken);
            if (!valid) {
                throw new IllegalArgumentException(
                        "One or more products are unavailable or out of stock");
            }
        }

        Order order = new Order();
        order.setUserId(req.userId());
        order.setIdempotencyKey(req.idempotencyKey());
        order.setShippingAddress(req.shippingAddress());
        order.setStatus(OrderStatus.PENDING);

        List<OrderItem> items = req.items().stream().map(dto -> {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(dto.productId());
            item.setProductName(dto.productName());
            item.setQuantity(dto.quantity());
            item.setUnitPrice(dto.unitPrice());
            item.setSubtotal(dto.unitPrice().multiply(BigDecimal.valueOf(dto.quantity())));
            return item;
        }).toList();

        order.setItems(items);

        BigDecimal total = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        log.info("Order placed orderId={} userId={} total={}", saved.getId(), saved.getUserId(), saved.getTotalAmount());

        kafkaProducerService.publishOrderPlaced(new OrderPlacedEvent(
                saved.getId().toString(),
                saved.getUserId(),
                saved.getTotalAmount(),
                saved.getShippingAddress(),
                saved.getItems().stream().map(this::mapItemToDto).toList()
        ));

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id, String requestingUserId, boolean isAdmin) {
        Order order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
        if (!isAdmin && !order.getUserId().equals(requestingUserId)) {
            throw new AccessDeniedException("Access denied to order " + id);
        }
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByUser(String userId, Pageable pageable) {
        Page<Order> page = orderRepository.findByUserId(userId, pageable);
        List<OrderResponse> items = page.getContent().stream().map(this::mapToResponse).toList();
        return new PagedResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public OrderResponse updateStatus(UUID id, UpdateStatusRequest req) {
        Order order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
        OrderStatus previous = order.getStatus();
        validateStatusTransition(previous, req.newStatus());
        order.setStatus(req.newStatus());
        Order saved = orderRepository.save(order);
        log.info("Order status updated orderId={} {} -> {}", id, previous, req.newStatus());

        kafkaProducerService.publishOrderStatusUpdated(new OrderStatusUpdatedEvent(
                saved.getId().toString(),
                saved.getUserId(),
                previous.name(),
                saved.getStatus().name()
        ));

        return mapToResponse(saved);
    }

    public OrderResponse cancelOrder(UUID id, String requestingUserId, boolean isAdmin) {
        Order order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);

        if (!isAdmin && !order.getUserId().equals(requestingUserId)) {
            throw new AccessDeniedException("Access denied to order " + id);
        }

        OrderStatus current = order.getStatus();
        if (current != OrderStatus.PENDING && current != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    "Cannot cancel order in status " + current + ". Only PENDING or CONFIRMED orders can be cancelled.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        log.info("Order cancelled orderId={} userId={}", id, requestingUserId);

        kafkaProducerService.publishOrderStatusUpdated(new OrderStatusUpdatedEvent(
                saved.getId().toString(),
                saved.getUserId(),
                current.name(),
                OrderStatus.CANCELLED.name()
        ));

        return mapToResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (next) {
            case CONFIRMED  -> current == OrderStatus.PENDING;
            case SHIPPED    -> current == OrderStatus.CONFIRMED;
            case DELIVERED  -> current == OrderStatus.SHIPPED;
            case CANCELLED  -> current == OrderStatus.PENDING || current == OrderStatus.CONFIRMED;
            default         -> false;
        };

        if (!valid) {
            throw new InvalidOrderStateException(
                    "Invalid status transition from " + current + " to " + next);
        }
    }

    private OrderResponse mapToResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getItems().stream().map(this::mapItemToDto).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemDto mapItemToDto(OrderItem item) {
        return new OrderItemDto(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }
}
