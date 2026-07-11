package com.ecommerce.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderPlacedEvent(

        String orderId,
        String userId,
        BigDecimal totalAmount,
        String shippingAddress,
        List<OrderItemDto> items
) {}
