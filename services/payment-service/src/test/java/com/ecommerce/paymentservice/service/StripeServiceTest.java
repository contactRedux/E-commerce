package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.enums.PaymentGateway;
import com.ecommerce.paymentservice.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentProcessingException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private StripeService stripeService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(stripeService, "webhookSecret", "whsec_test");
    }

    // ── createPaymentIntent ────────────────────────────────────────────────────

    @Test
    void createPaymentIntent_success_persistsPaymentAndReturnsResponse() throws Exception {
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_123");
        when(mockIntent.getClientSecret()).thenReturn("pi_test_123_secret");

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(), any())).thenReturn(mockIntent);

            PaymentIntentResponse response = stripeService.createPaymentIntent(
                    "order-1", "user-1", new BigDecimal("49.99"), "USD", "idem-1");

            assertThat(response.gatewayPaymentId()).isEqualTo("pi_test_123");
            assertThat(response.clientSecret()).isEqualTo("pi_test_123_secret");
            assertThat(response.status()).isEqualTo("PENDING");

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getGateway()).isEqualTo(PaymentGateway.STRIPE);
            assertThat(saved.getGatewayPaymentId()).isEqualTo("pi_test_123");
        }
    }

    @Test
    void createPaymentIntent_idempotent_returnsExistingWithoutCallingStripe() {
        Payment existing = buildPayment("pi_existing", PaymentStatus.PENDING);
        when(paymentRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.of(existing));

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            PaymentIntentResponse response = stripeService.createPaymentIntent(
                    "order-2", "user-1", new BigDecimal("10.00"), "USD", "idem-2");

            assertThat(response.gatewayPaymentId()).isEqualTo("pi_existing");
            mocked.verify(() -> PaymentIntent.create(any(), any()), never());
        }
    }

    // ── handleWebhookEvent ─────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_paymentSucceeded_updatesStatusToSucceeded() throws Exception {
        Payment payment = buildPayment("pi_success", PaymentStatus.PENDING);
        when(paymentRepository.findByGatewayPaymentId("pi_success")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        Event event = buildEvent("payment_intent.succeeded", "pi_success");

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
    }

    @Test
    void handleWebhookEvent_paymentFailed_updatesStatusToFailed() throws Exception {
        Payment payment = buildPayment("pi_failed", PaymentStatus.PENDING);
        when(paymentRepository.findByGatewayPaymentId("pi_failed")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        Event event = buildEvent("payment_intent.payment_failed", "pi_failed");

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @Test
    void handleWebhookEvent_invalidSignature_throwsPaymentProcessingException() {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("invalid", "sig"));

            assertThatThrownBy(() -> stripeService.handleWebhookEvent("payload", "bad-sig"))
                    .isInstanceOf(PaymentProcessingException.class)
                    .hasMessageContaining("Invalid Stripe webhook signature");
        }
    }

    // ── refund ────────────────────────────────────────────────────────────────

    @Test
    void refund_success_setsStatusToRefunded() throws Exception {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment("pi_refund", PaymentStatus.SUCCEEDED);
        payment.setId(id);
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        try (MockedStatic<Refund> mocked = mockStatic(Refund.class)) {
            Refund mockRefund = mock(Refund.class);
            mocked.when(() -> Refund.create(any())).thenReturn(mockRefund);

            stripeService.refund(id);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }
    }

    @Test
    void refund_notSucceeded_throwsPaymentProcessingException() {
        UUID id = UUID.randomUUID();
        Payment payment = buildPayment("pi_pending", PaymentStatus.PENDING);
        payment.setId(id);
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> stripeService.refund(id))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("Cannot refund");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Payment buildPayment(String gatewayId, PaymentStatus status) {
        Payment p = new Payment();
        p.setOrderId("order-1");
        p.setIdempotencyKey("idem-" + gatewayId);
        p.setGateway(PaymentGateway.STRIPE);
        p.setGatewayPaymentId(gatewayId);
        p.setGatewayClientSecret(gatewayId + "_secret");
        p.setAmount(new BigDecimal("49.99"));
        p.setCurrency("USD");
        p.setStatus(status);
        return p;
    }

    private Event buildEvent(String type, String paymentIntentId) {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn(paymentIntentId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(pi));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }
}
