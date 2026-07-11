package com.ecommerce.productservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        String categoryId,
        Integer stockQuantity,
        String imageUrl,
        Map<String, String> attributes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
