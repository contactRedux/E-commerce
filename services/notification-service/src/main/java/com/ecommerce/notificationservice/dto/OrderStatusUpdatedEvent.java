package com.ecommerce.notificationservice.dto;

public record OrderStatusUpdatedEvent(
        String orderId,
        String userId,
        String previousStatus,
        String newStatus
) {}
