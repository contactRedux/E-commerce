package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.config.PayPalConfig;
import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.dto.PaymentProcessedEvent;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.enums.PaymentGateway;
import com.ecommerce.paymentservice.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.exception.PaymentProcessingException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class PayPalService {

    private static final Logger log = LoggerFactory.getLogger(PayPalService.class);

    private final WebClient paypalWebClient;
    private final PayPalConfig payPalConfig;
    private final PaymentRepository paymentRepository;
    private final KafkaProducerService kafkaProducerService;

    public PayPalService(
            @Qualifier("paypalWebClient") WebClient paypalWebClient,
            PayPalConfig payPalConfig,
            PaymentRepository paymentRepository,
            KafkaProducerService kafkaProducerService) {
        this.paypalWebClient = paypalWebClient;
        this.payPalConfig = payPalConfig;
        this.paymentRepository = paymentRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    // Step 1: Obtain OAuth2 access token via client credentials
    String getAccessToken() {
        String credentials = payPalConfig.getClientId() + ":" + payPalConfig.getClientSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        Map<?, ?> response = paypalWebClient.post()
                .uri("/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new PaymentProcessingException("PayPal OAuth token request failed"))))
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("access_token")) {
            throw new PaymentProcessingException("PayPal OAuth token response missing access_token");
        }
        return (String) response.get("access_token");
    }

    // Step 2: Create a PayPal Order
    @Transactional
    public PaymentIntentResponse createOrder(
            String orderId, String userId, BigDecimal amount, String currency, String idempotencyKey) {

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> new PaymentIntentResponse(
                        existing.getGatewayPaymentId(),
                        null,
                        existing.getStatus().name()))
                .orElseGet(() -> doCreateOrder(orderId, userId, amount, currency, idempotencyKey));
    }

    private PaymentIntentResponse doCreateOrder(
            String orderId, String userId, BigDecimal amount, String currency, String idempotencyKey) {

        String token = getAccessToken();

        Map<String, Object> requestBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", orderId,
                        "amount", Map.of(
                                "currency_code", currency.toUpperCase(),
                                "value", amount.toPlainString()
                        )
                ))
        );

        Map<?, ?> response = paypalWebClient.post()
                .uri("/v2/checkout/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("PayPal-Request-Id", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new PaymentProcessingException("PayPal create order failed"))))
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("id")) {
            throw new PaymentProcessingException("PayPal create order response missing order ID");
        }

        String paypalOrderId = (String) response.get("id");

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setGateway(PaymentGateway.PAYPAL);
        payment.setGatewayPaymentId(paypalOrderId);
        payment.setAmount(amount);
        payment.setCurrency(currency.toUpperCase());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        log.info("PayPal order created paypalOrderId={} orderId={}", paypalOrderId, orderId);

        return new PaymentIntentResponse(paypalOrderId, null, PaymentStatus.PENDING.name());
    }

    // Step 3: Capture a PayPal Order after buyer approval
    @Transactional
    public void captureOrder(String paypalOrderId) {
        String token = getAccessToken();

        Map<?, ?> response = paypalWebClient.post()
                .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new PaymentProcessingException("PayPal capture order failed"))))
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new PaymentProcessingException("PayPal capture response was empty");
        }

        String captureStatus = (String) response.get("status");
        log.info("PayPal order captured paypalOrderId={} captureStatus={}", paypalOrderId, captureStatus);

        Payment payment = paymentRepository.findByGatewayPaymentId(paypalOrderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for PayPal order: " + paypalOrderId));

        PaymentStatus newStatus = "COMPLETED".equalsIgnoreCase(captureStatus)
                ? PaymentStatus.SUCCEEDED
                : PaymentStatus.FAILED;

        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        if (newStatus == PaymentStatus.SUCCEEDED) {
            kafkaProducerService.publishPaymentProcessed(new PaymentProcessedEvent(
                    payment.getId().toString(),
                    payment.getOrderId(),
                    null,
                    payment.getAmount(),
                    payment.getCurrency(),
                    PaymentStatus.SUCCEEDED.name(),
                    PaymentGateway.PAYPAL.name()
            ));
        }
    }
}
