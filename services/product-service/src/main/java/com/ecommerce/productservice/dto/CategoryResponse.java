package com.ecommerce.productservice.dto;

import java.time.LocalDateTime;

public record CategoryResponse(
        String id,
        String name,
        String description,
        String parentCategoryId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
