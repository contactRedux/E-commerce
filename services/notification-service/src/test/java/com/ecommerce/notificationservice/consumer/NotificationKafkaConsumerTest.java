package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.dto.OrderItemDto;
import com.ecommerce.notificationservice.dto.OrderPlacedEvent;
import com.ecommerce.notificationservice.dto.OrderStatusUpdatedEvent;
import com.ecommerce.notificationservice.dto.PaymentProcessedEvent;
import com.ecommerce.notificationservice.service.EmailService;
import com.ecommerce.notificationservice.service.SmsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    private ObjectMapper objectMapper;
    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new NotificationKafkaConsumer(emailService, smsService, objectMapper);
    }

    // ---- order.placed ----

    @Test
    void handleOrderPlaced_validMessage_callsSendOrderConfirmation() throws Exception {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-uuid-1234-5678", "user-001",
                new BigDecimal("49.99"), "123 Main St",
                List.of(new OrderItemDto("prod-1", "Widget", 1, new BigDecimal("49.99"))));

        String json = objectMapper.writeValueAsString(event);

        consumer.handleOrderPlaced(json);

        verify(emailService).sendOrderConfirmation(event);
    }

    @Test
    void handleOrderPlaced_malformedJson_doesNotPropagateException() {
        assertThatNoException().isThrownBy(() ->
                consumer.handleOrderPlaced("{invalid-json}"));

        verifyNoInteractions(emailService);
    }

    // ---- payment.processed ----

    @Test
    void handlePaymentProcessed_statusSucceeded_callsSendPaymentReceipt() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-001", "order-uuid-1234-5678", "user-001",
                new BigDecimal("49.99"), "USD", "SUCCEEDED", "STRIPE");

        consumer.handlePaymentProcessed(objectMapper.writeValueAsString(event));

        verify(emailService).sendPaymentReceipt(event);
        verify(emailService, never()).sendPaymentFailedNotification(event);
    }

    @Test
    void handlePaymentProcessed_statusFailed_callsSendPaymentFailedNotification() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-002", "order-uuid-1234-5678", "user-001",
                new BigDecimal("49.99"), "USD", "FAILED", "STRIPE");

        consumer.handlePaymentProcessed(objectMapper.writeValueAsString(event));

        verify(emailService).sendPaymentFailedNotification(event);
        verify(emailService, never()).sendPaymentReceipt(event);
    }

    @Test
    void handlePaymentProcessed_malformedJson_doesNotPropagateException() {
        assertThatNoException().isThrownBy(() ->
                consumer.handlePaymentProcessed("{bad}"));

        verifyNoInteractions(emailService);
    }

    // ---- order.status.updated ----

    @Test
    void handleOrderStatusUpdated_validMessage_callsEmailAndSms() throws Exception {
        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(
                "order-uuid-1234-5678", "user-001", "PAID", "SHIPPED");

        consumer.handleOrderStatusUpdated(objectMapper.writeValueAsString(event));

        verify(emailService).sendShippingUpdate(event);
        verify(smsService).sendOrderStatusSms(event);
    }

    @Test
    void handleOrderStatusUpdated_malformedJson_doesNotPropagateException() {
        assertThatNoException().isThrownBy(() ->
                consumer.handleOrderStatusUpdated("{bad}"));

        verifyNoInteractions(emailService);
        verifyNoInteractions(smsService);
    }
}
