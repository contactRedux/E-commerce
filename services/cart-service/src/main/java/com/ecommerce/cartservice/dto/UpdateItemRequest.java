package com.ecommerce.cartservice.dto;

import jakarta.validation.constraints.Min;

public record UpdateItemRequest(
        @Min(value = 0, message = "quantity must be 0 or greater") int quantity
) {}
