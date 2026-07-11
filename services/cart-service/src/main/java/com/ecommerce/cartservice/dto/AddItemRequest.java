package com.ecommerce.cartservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddItemRequest(
        @NotBlank(message = "productId must not be blank") String productId,
        @NotBlank(message = "productName must not be blank") String productName,
        @Min(value = 1, message = "quantity must be at least 1") int quantity,
        @NotNull(message = "unitPrice must not be null")
        @DecimalMin(value = "0.01", message = "unitPrice must be at least 0.01") BigDecimal unitPrice
) {}
