package com.ecommerce.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        UUID id,
        String orderId,
        String gateway,
        String gatewayPaymentId,
        // gatewayClientSecret intentionally omitted from subsequent fetch responses;
        // only populated on creation (see PaymentService.toResponse)
        String gatewayClientSecret,
        BigDecimal amount,
        String currency,
        String status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
