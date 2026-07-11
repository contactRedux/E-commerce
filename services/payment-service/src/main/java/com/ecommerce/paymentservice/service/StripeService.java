package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.dto.PaymentProcessedEvent;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.enums.PaymentGateway;
import com.ecommerce.paymentservice.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentProcessingException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final PaymentRepository paymentRepository;
    private final KafkaProducerService kafkaProducerService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            String orderId, String userId, BigDecimal amount, String currency, String idempotencyKey) {

        // Idempotency: return existing record if already processed
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> new PaymentIntentResponse(
                        existing.getGatewayPaymentId(),
                        existing.getGatewayClientSecret(),
                        existing.getStatus().name()))
                .orElseGet(() -> doCreatePaymentIntent(orderId, userId, amount, currency, idempotencyKey));
    }

    private PaymentIntentResponse doCreatePaymentIntent(
            String orderId, String userId, BigDecimal amount, String currency, String idempotencyKey) {

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency(currency.toLowerCase())
                .putMetadata("orderId", orderId)
                .putMetadata("userId", userId)
                .putMetadata("idempotencyKey", idempotencyKey)
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        PaymentIntent intent;
        try {
            intent = PaymentIntent.create(params, options);
        } catch (StripeException e) {
            log.error("Stripe PaymentIntent creation failed for orderId={}", orderId,
                    e);
            throw new PaymentProcessingException("Failed to create Stripe PaymentIntent: " + e.getMessage());
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setGateway(PaymentGateway.STRIPE);
        payment.setGatewayPaymentId(intent.getId());
        payment.setGatewayClientSecret(intent.getClientSecret());
        payment.setAmount(amount);
        payment.setCurrency(currency.toUpperCase());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        log.info("Stripe PaymentIntent created paymentId={} orderId={}", intent.getId(), orderId);

        return new PaymentIntentResponse(intent.getId(), intent.getClientSecret(), PaymentStatus.PENDING.name());
    }

    @Transactional
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            throw new PaymentProcessingException("Invalid Stripe webhook signature");
        }

        String eventType = event.getType();
        log.info("Received Stripe webhook event type={}", eventType);

        switch (eventType) {
            case "payment_intent.succeeded" -> {
                String paymentIntentId = extractPaymentIntentId(event);
                updatePaymentStatus(paymentIntentId, PaymentStatus.SUCCEEDED, null);
                publishEvent(paymentIntentId, PaymentStatus.SUCCEEDED);
            }
            case "payment_intent.payment_failed" -> {
                String paymentIntentId = extractPaymentIntentId(event);
                String failureMessage = extractFailureMessage(event);
                updatePaymentStatus(paymentIntentId, PaymentStatus.FAILED, failureMessage);
            }
            default -> log.debug("Unhandled Stripe event type={}", eventType);
        }
    }

    @Transactional
    public void refund(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentProcessingException(
                    "Cannot refund payment with status: " + payment.getStatus());
        }

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(payment.getGatewayPaymentId())
                .build();

        try {
            Refund.create(params);
        } catch (StripeException e) {
            log.error("Stripe refund failed for paymentId={}", paymentId, e);
            throw new PaymentProcessingException("Stripe refund failed: " + e.getMessage());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        log.info("Stripe refund completed paymentId={} orderId={}", paymentId, payment.getOrderId());

        kafkaProducerService.publishPaymentProcessed(new PaymentProcessedEvent(
                payment.getId().toString(),
                payment.getOrderId(),
                null,
                payment.getAmount(),
                payment.getCurrency(),
                PaymentStatus.REFUNDED.name(),
                PaymentGateway.STRIPE.name()
        ));
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void updatePaymentStatus(String gatewayPaymentId, PaymentStatus status, String failureReason) {
        paymentRepository.findByGatewayPaymentId(gatewayPaymentId).ifPresent(payment -> {
            payment.setStatus(status);
            if (failureReason != null) {
                payment.setFailureReason(failureReason);
            }
            paymentRepository.save(payment);
            log.info("Payment status updated to {} for gatewayPaymentId={}", status, gatewayPaymentId);
        });
    }

    private void publishEvent(String gatewayPaymentId, PaymentStatus status) {
        paymentRepository.findByGatewayPaymentId(gatewayPaymentId).ifPresent(payment ->
                kafkaProducerService.publishPaymentProcessed(new PaymentProcessedEvent(
                        payment.getId().toString(),
                        payment.getOrderId(),
                        null,
                        payment.getAmount(),
                        payment.getCurrency(),
                        status.name(),
                        PaymentGateway.STRIPE.name()
                ))
        );
    }

    private String extractPaymentIntentId(Event event) {
        var dataObject = event.getDataObjectDeserializer().getObject();
        if (dataObject.isPresent() && dataObject.get() instanceof PaymentIntent pi) {
            return pi.getId();
        }
        throw new PaymentProcessingException("Unable to extract PaymentIntent ID from event");
    }

    private String extractFailureMessage(Event event) {
        var dataObject = event.getDataObjectDeserializer().getObject();
        if (dataObject.isPresent() && dataObject.get() instanceof PaymentIntent pi
                && pi.getLastPaymentError() != null) {
            return pi.getLastPaymentError().getMessage();
        }
        return "Payment failed";
    }
}
