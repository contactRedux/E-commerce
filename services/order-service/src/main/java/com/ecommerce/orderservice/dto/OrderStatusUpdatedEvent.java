package com.ecommerce.orderservice.dto;

public record OrderStatusUpdatedEvent(

        String orderId,
        String userId,
        String previousStatus,
        String newStatus
) {}
