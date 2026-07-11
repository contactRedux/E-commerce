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
import org.thymeleaf.TemplateEngine;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailService}.
 *
 * <p>The Thymeleaf {@link TemplateEngine} is mocked so tests run without a Spring
 * context and without actual template files being resolved at test time. All
 * test paths exercise the SendGrid interaction and error handling.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private SendGrid sendGrid;

    @Mock
    private TemplateEngine templateEngine;

    private EmailService emailService;

    private static final String FROM_EMAIL = "noreply@ecommerce.local";

    @BeforeEach
    void setUp() {
        // Stub the template engine to return a simple HTML string for any template
        when(templateEngine.process(anyString(), any())).thenReturn("<html><body>test</body></html>");
        emailService = new EmailService(sendGrid, templateEngine, FROM_EMAIL);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Response okResponse() {
        Response r = new Response();
        r.setStatusCode(202);
        r.setBody("");
        return r;
    }

    private Response errorResponse() {
        Response r = new Response();
        r.setStatusCode(400);
        r.setBody("Bad Request");
        return r;
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

    // ─────────────────────────────────────────────────────────────────────────
    // sendOrderConfirmation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sendOrderConfirmation_success_callsSendGridAndReturnsNormally() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        assertThatNoException().isThrownBy(() ->
                emailService.sendOrderConfirmation(sampleOrderPlacedEvent()));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGrid).api(captor.capture());
        assertThat(captor.getValue().getEndpoint()).isEqualTo("mail/send");
    }

    @Test
    void sendOrderConfirmation_usesOrderConfirmationTemplate() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        emailService.sendOrderConfirmation(sampleOrderPlacedEvent());

        verify(templateEngine).process(eq("email/order-confirmation"), any());
    }

    @Test
    void sendOrderConfirmation_apiError_throwsNotificationException() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(errorResponse());

        assertThatThrownBy(() -> emailService.sendOrderConfirmation(sampleOrderPlacedEvent()))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Email delivery failed");
    }

    @Test
    void sendOrderConfirmation_ioException_throwsNotificationException() throws IOException {
        when(sendGrid.api(any(Request.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> emailService.sendOrderConfirmation(sampleOrderPlacedEvent()))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Email delivery failed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendPaymentReceipt
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sendPaymentReceipt_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-001", "order-uuid-1234-5678", "user-001",
                new BigDecimal("99.99"), "USD", "SUCCEEDED", "STRIPE");

        assertThatNoException().isThrownBy(() -> emailService.sendPaymentReceipt(event));
        verify(templateEngine).process(eq("email/payment-receipt"), any());
        verify(sendGrid).api(any(Request.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendPaymentFailedNotification
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sendPaymentFailedNotification_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-002", "order-uuid-1234-5678", "user-001",
                new BigDecimal("99.99"), "USD", "FAILED", "STRIPE");

        assertThatNoException().isThrownBy(() -> emailService.sendPaymentFailedNotification(event));
        verify(templateEngine).process(eq("email/payment-failed"), any());
        verify(sendGrid).api(any(Request.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendShippingUpdate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sendShippingUpdate_success() throws IOException {
        when(sendGrid.api(any(Request.class))).thenReturn(okResponse());

        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(
                "order-uuid-1234-5678", "user-001", "PAID", "SHIPPED");

        assertThatNoException().isThrownBy(() -> emailService.sendShippingUpdate(event));
        verify(templateEngine).process(eq("email/shipping-update"), any());
        verify(sendGrid).api(any(Request.class));
    }
}
