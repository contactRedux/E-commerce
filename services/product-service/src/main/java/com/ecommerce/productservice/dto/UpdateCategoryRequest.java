package com.ecommerce.productservice.dto;

public record UpdateCategoryRequest(
        String name,
        String description,
        String parentCategoryId
) {}
