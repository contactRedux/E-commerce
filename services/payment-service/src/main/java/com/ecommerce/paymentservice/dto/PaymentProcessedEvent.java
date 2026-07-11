package com.ecommerce.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentProcessedEvent(
        String paymentId,
        String orderId,
        String userId,
        BigDecimal amount,
        String currency,
        String status,   // SUCCEEDED, FAILED, REFUNDED
        String gateway
) {}
