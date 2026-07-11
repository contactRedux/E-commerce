package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.ApiResponse;
import com.ecommerce.paymentservice.dto.CreatePayPalOrderRequest;
import com.ecommerce.paymentservice.dto.CreatePaymentIntentRequest;
import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.service.PaymentService;
import com.ecommerce.paymentservice.service.StripeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final StripeService stripeService;

    /**
     * Create a Stripe PaymentIntent and return the client_secret for front-end confirmation.
     * Requires JWT authentication.
     */
    @PostMapping("/stripe/intent")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createStripeIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) {

        PaymentIntentResponse response = paymentService.createStripePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Create a PayPal Order and return the PayPal order ID for buyer approval redirect.
     * Requires JWT authentication.
     */
    @PostMapping("/paypal/order")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPayPalOrder(
            @Valid @RequestBody CreatePayPalOrderRequest request) {

        PaymentIntentResponse response = paymentService.createPayPalOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Capture an approved PayPal order. Called by the client after buyer approves on PayPal.
     * Requires JWT authentication.
     */
    @PostMapping("/paypal/capture/{paypalOrderId}")
    public ResponseEntity<ApiResponse<Void>> capturePayPalOrder(
            @PathVariable String paypalOrderId) {

        paymentService.capturePayPalOrder(paypalOrderId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Stripe webhook endpoint — NOT protected by JWT.
     * Stripe signature verification is performed in {@link StripeService#handleWebhookEvent}.
     */
    @PostMapping("/webhook/stripe")
    public ResponseEntity<ApiResponse<Void>> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        stripeService.handleWebhookEvent(payload, sigHeader);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Initiate a refund for a Stripe payment.
     * Requires JWT authentication. Only the payment owner or an ADMIN should call this.
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<Void>> refund(@PathVariable UUID id) {
        paymentService.refund(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Retrieve payment details by order ID.
     * Requires JWT authentication.
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable String orderId) {
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
