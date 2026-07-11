package com.ecommerce.paymentservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RefundRequest(
        @NotNull UUID paymentId
) {}
