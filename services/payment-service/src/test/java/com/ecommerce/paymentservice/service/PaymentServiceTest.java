package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePayPalOrderRequest;
import com.ecommerce.paymentservice.dto.CreatePaymentIntentRequest;
import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.enums.PaymentGateway;
import com.ecommerce.paymentservice.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StripeService stripeService;

    @Mock
    private PayPalService payPalService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createStripePayment_delegatesToStripeService() {
        CreatePaymentIntentRequest req = new CreatePaymentIntentRequest(
                "order-1", "user-1", new BigDecimal("99.99"), "USD", "idem-1");

        PaymentIntentResponse expected = new PaymentIntentResponse("pi_123", "secret_123", "PENDING");
        when(stripeService.createPaymentIntent(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(expected);

        PaymentIntentResponse result = paymentService.createStripePayment(req);

        assertThat(result).isEqualTo(expected);
        verify(stripeService).createPaymentIntent("order-1", "user-1",
                new BigDecimal("99.99"), "USD", "idem-1");
    }

    @Test
    void createPayPalOrder_delegatesToPayPalService() {
        CreatePayPalOrderRequest req = new CreatePayPalOrderRequest(
                "order-2", "user-2", new BigDecimal("50.00"), "EUR", "idem-2");

        PaymentIntentResponse expected = new PaymentIntentResponse("PAYPAL_ORDER_123", null, "PENDING");
        when(payPalService.createOrder(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(expected);

        PaymentIntentResponse result = paymentService.createPayPalOrder(req);

        assertThat(result).isEqualTo(expected);
        verify(payPalService).createOrder("order-2", "user-2",
                new BigDecimal("50.00"), "EUR", "idem-2");
    }

    @Test
    void capturePayPalOrder_delegatesToPayPalService() {
        paymentService.capturePayPalOrder("PAYPAL_ORDER_123");
        verify(payPalService).captureOrder("PAYPAL_ORDER_123");
    }

    @Test
    void getPaymentByOrderId_returnsPaymentResponse() {
        Payment payment = buildPayment();
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrderId("order-1");

        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.status()).isEqualTo("PENDING");
        // client secret must not be exposed in read responses
        assertThat(response.gatewayClientSecret()).isNull();
    }

    @Test
    void getPaymentByOrderId_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.findByOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId("missing"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void refund_delegatesToStripeService() {
        UUID id = UUID.randomUUID();
        paymentService.refund(id);
        verify(stripeService).refund(id);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Payment buildPayment() {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId("order-1");
        p.setIdempotencyKey("idem-1");
        p.setGateway(PaymentGateway.STRIPE);
        p.setGatewayPaymentId("pi_123");
        p.setGatewayClientSecret("pi_123_secret");
        p.setAmount(new BigDecimal("99.99"));
        p.setCurrency("USD");
        p.setStatus(PaymentStatus.PENDING);
        return p;
    }
}
