package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required") String name,
        String description,
        String parentCategoryId
) {}
