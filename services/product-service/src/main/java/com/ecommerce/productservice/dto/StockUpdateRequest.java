package com.ecommerce.productservice.dto;

import jakarta.validation.constraints.NotNull;

public record StockUpdateRequest(
        @NotNull(message = "Delta is required") Integer delta
) {}
