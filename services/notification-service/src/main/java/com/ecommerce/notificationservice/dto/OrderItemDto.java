package com.ecommerce.notificationservice.dto;

import java.math.BigDecimal;

public record OrderItemDto(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {}
