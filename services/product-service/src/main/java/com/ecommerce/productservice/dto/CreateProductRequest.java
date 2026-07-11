package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank(message = "Product name is required") String name,
        String description,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", message = "Price must be non-negative") BigDecimal price,
        String categoryId,
        @Min(value = 0, message = "Stock quantity must be non-negative") Integer stockQuantity,
        String imageUrl,
        Map<String, String> attributes
) {}
