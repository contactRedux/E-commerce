package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.OrderItemDto;
import com.ecommerce.notificationservice.dto.OrderPlacedEvent;
import com.ecommerce.notificationservice.dto.OrderStatusUpdatedEvent;
import com.ecommerce.notificationservice.dto.PaymentProcessedEvent;
import com.ecommerce.notificationservice.exception.NotificationException;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SendGrid sendGrid;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(sendGrid, "noreply@ecommerce.local");
    }

    private Response okResponse() {
        Response response = new Response();
        response.setStatusCode(202);
        response.setBody("");
        return response;
    }

    private Response errorResponse() {
        Response response = new Response();
        response.setStatusCode(400);
        response.setBody("Bad Request");
        return response;
    }

    private OrderPlacedEvent sampleOrderPlacedEvent() {
        return new OrderPlacedEvent(
                "order-uuid-1234-5678",
                "user-001",
                new BigDecimal("99.99"),
                "123 Main St",
                List.of(new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("49.99")))
        );
    }

    @Test
    void sendOrderConfirmation_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        assertThatNoException().isThrownBy(() ->
                emailService.sendOrderConfirmation(sampleOrderPlacedEvent()));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGrid).api(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getEndpoint())
                .isEqualTo("mail/send");
    }

    @Test
    void sendOrderConfirmation_apiError_throwsNotificationException() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(errorResponse());

        assertThatThrownBy(() -> emailService.sendOrderConfirmation(sampleOrderPlacedEvent()))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Email delivery failed");
    }

    @Test
    void sendPaymentReceipt_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-001", "order-uuid-1234-5678", "user-001",
                new BigDecimal("99.99"), "USD", "SUCCEEDED", "STRIPE");

        assertThatNoException().isThrownBy(() -> emailService.sendPaymentReceipt(event));
        verify(sendGrid).api(any(Request.class));
    }

    @Test
    void sendPaymentFailedNotification_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-002", "order-uuid-1234-5678", "user-001",
                new BigDecimal("99.99"), "USD", "FAILED", "STRIPE");

        assertThatNoException().isThrownBy(() -> emailService.sendPaymentFailedNotification(event));
        verify(sendGrid).api(any(Request.class));
    }

    @Test
    void sendShippingUpdate_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(
                "order-uuid-1234-5678", "user-001", "PAID", "SHIPPED");

        assertThatNoException().isThrownBy(() -> emailService.sendShippingUpdate(event));
        verify(sendGrid).api(any(Request.class));
    }

    @Test
    void sendEmail_ioException_throwsNotificationException() throws IOException {
        when(sendGrid.api(any(Request.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> emailService.sendOrderConfirmation(sampleOrderPlacedEvent()))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Email delivery failed");
    }
}
