package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePayPalOrderRequest;
import com.ecommerce.paymentservice.dto.CreatePaymentIntentRequest;
import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;
    private final PayPalService payPalService;

    @Transactional
    public PaymentIntentResponse createStripePayment(CreatePaymentIntentRequest req) {
        return stripeService.createPaymentIntent(
                req.orderId(), req.userId(), req.amount(), req.currency(), req.idempotencyKey());
    }

    @Transactional
    public PaymentIntentResponse createPayPalOrder(CreatePayPalOrderRequest req) {
        return payPalService.createOrder(
                req.orderId(), req.userId(), req.amount(), req.currency(), req.idempotencyKey());
    }

    @Transactional
    public void capturePayPalOrder(String paypalOrderId) {
        payPalService.captureOrder(paypalOrderId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for orderId: " + orderId));
        return toResponse(payment, false);
    }

    @Transactional
    public void refund(UUID paymentId) {
        stripeService.refund(paymentId);
    }

    // ── mapping ────────────────────────────────────────────────────────────────

    /**
     * Maps a Payment entity to its DTO.
     *
     * @param includeClientSecret {@code true} only on initial creation response so the
     *                            Stripe {@code client_secret} is returned to the caller once;
     *                            {@code false} on all subsequent read responses for security.
     */
    public PaymentResponse toResponse(Payment payment, boolean includeClientSecret) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getGateway().name(),
                payment.getGatewayPaymentId(),
                includeClientSecret ? payment.getGatewayClientSecret() : null,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
