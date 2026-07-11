package com.ecommerce.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentIntentResponse(
        String gatewayPaymentId,
        String clientSecret,   // Stripe client_secret for front-end confirmation; null for PayPal
        String status
) {}
