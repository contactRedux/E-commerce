package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(

        @NotNull(message = "newStatus is required")
        OrderStatus newStatus
) {}
