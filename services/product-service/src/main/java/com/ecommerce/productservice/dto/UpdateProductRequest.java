package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateProductRequest(
        String name,
        String description,
        @DecimalMin(value = "0.0", message = "Price must be non-negative") BigDecimal price,
        String categoryId,
        @Min(value = 0, message = "Stock quantity must be non-negative") Integer stockQuantity,
        String imageUrl,
        Map<String, String> attributes
) {}
