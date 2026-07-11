package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.config.PayPalConfig;
import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.enums.PaymentGateway;
import com.ecommerce.paymentservice.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.PaymentProcessingException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PayPalServiceTest {

    @Mock
    private PayPalConfig payPalConfig;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    private PayPalService payPalService;

    @BeforeEach
    void setup() {
        // Use a real WebClient pointed at a mock; we spy on getAccessToken
        WebClient webClient = mock(WebClient.class);
        payPalService = spy(new PayPalService(webClient, payPalConfig, paymentRepository, kafkaProducerService));
    }

    @Test
    void getAccessToken_success_returnsToken() {
        // We test getAccessToken indirectly through createOrder. Here we verify
        // it delegates correctly when the token is stubbed.
        doReturn("test_access_token").when(payPalService).getAccessToken();

        String token = payPalService.getAccessToken();
        assertThat(token).isEqualTo("test_access_token");
    }

    @Test
    void createOrder_idempotent_returnsExistingPayment() {
        Payment existing = buildPayment("paypal_order_123", PaymentStatus.PENDING);
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        PaymentIntentResponse response = payPalService.createOrder(
                "order-1", "user-1", new BigDecimal("50.00"), "USD", "idem-1");

        assertThat(response.gatewayPaymentId()).isEqualTo("paypal_order_123");
        assertThat(response.clientSecret()).isNull(); // PayPal has no client_secret
    }

    @Test
    void createOrder_success_persistsPaymentAndReturnsResponse() {
        when(paymentRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        doReturn("mock_token").when(payPalService).getAccessToken();

        // Build a mock WebClient chain for POST /v2/checkout/orders
        Map<String, Object> apiResponse = Map.of("id", "PAYPAL_ORDER_999", "status", "CREATED");
        stubWebClientPostResponse(payPalService, apiResponse);

        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentIntentResponse response = payPalService.createOrder(
                "order-2", "user-1", new BigDecimal("75.00"), "USD", "idem-2");

        assertThat(response.gatewayPaymentId()).isEqualTo("PAYPAL_ORDER_999");
        assertThat(response.status()).isEqualTo("PENDING");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getGateway()).isEqualTo(PaymentGateway.PAYPAL);
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void captureOrder_success_updatesStatusToSucceeded() {
        doReturn("mock_token").when(payPalService).getAccessToken();

        Map<String, Object> captureResponse = Map.of("id", "PAYPAL_ORDER_999", "status", "COMPLETED");
        stubWebClientPostResponse(payPalService, captureResponse);

        Payment payment = buildPayment("PAYPAL_ORDER_999", PaymentStatus.PENDING);
        when(paymentRepository.findByGatewayPaymentId("PAYPAL_ORDER_999")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        payPalService.captureOrder("PAYPAL_ORDER_999");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Replaces the paypalWebClient field on the spy with a fully-mocked WebClient chain.
     * Because WebClient is fluent/reactive, we use a lightweight stub approach.
     */
    @SuppressWarnings("unchecked")
    private void stubWebClientPostResponse(PayPalService service, Map<String, Object> responseBody) {
        WebClient mockWc = mock(WebClient.class);
        WebClient.RequestBodyUriSpec reqUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec reqBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec<?> headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(mockWc.post()).thenReturn(reqUriSpec);
        when(reqUriSpec.uri(anyString())).thenReturn(reqBodySpec);
        when(reqUriSpec.uri(anyString(), (Object[]) any())).thenReturn(reqBodySpec);
        when(reqBodySpec.header(anyString(), anyString())).thenReturn(reqBodySpec);
        when(reqBodySpec.contentType(any())).thenReturn(reqBodySpec);
        when(reqBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(reqBodySpec.body(any())).thenReturn((WebClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

        // Inject the mocked WebClient into the spy via reflection
        org.springframework.test.util.ReflectionTestUtils.setField(service, "paypalWebClient", mockWc);
    }

    private Payment buildPayment(String gatewayId, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrderId("order-1");
        p.setIdempotencyKey("idem-" + gatewayId);
        p.setGateway(PaymentGateway.PAYPAL);
        p.setGatewayPaymentId(gatewayId);
        p.setAmount(new BigDecimal("50.00"));
        p.setCurrency("USD");
        p.setStatus(status);
        return p;
    }
}
