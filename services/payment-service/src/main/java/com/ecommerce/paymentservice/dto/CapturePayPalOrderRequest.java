package com.ecommerce.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CapturePayPalOrderRequest(
        @NotBlank String paypalOrderId
) {}
