package com.ecommerce.notificationservice.dto;

import java.math.BigDecimal;

public record PaymentProcessedEvent(
        String paymentId,
        String orderId,
        String userId,
        BigDecimal amount,
        String currency,
        String status,
        String gateway
) {}
