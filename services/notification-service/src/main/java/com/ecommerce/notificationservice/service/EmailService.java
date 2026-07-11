package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.OrderPlacedEvent;
import com.ecommerce.notificationservice.dto.OrderStatusUpdatedEvent;
import com.ecommerce.notificationservice.dto.PaymentProcessedEvent;
import com.ecommerce.notificationservice.exception.NotificationException;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;

/**
 * Sends transactional emails via SendGrid.
 *
 * <p>Email bodies are rendered using Thymeleaf HTML templates located under
 * {@code classpath:templates/email/}. Each public method accepts a domain event
 * DTO, populates a Thymeleaf {@link Context}, renders the template, and dispatches
 * the resulting HTML through the SendGrid API.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final SendGrid sendGrid;
    private final TemplateEngine templateEngine;
    private final String fromEmail;

    public EmailService(SendGrid sendGrid,
                        TemplateEngine templateEngine,
                        @Value("${notification.from-email}") String fromEmail) {
        this.sendGrid       = sendGrid;
        this.templateEngine = templateEngine;
        this.fromEmail      = fromEmail;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void sendOrderConfirmation(OrderPlacedEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderId",         event.orderId());
        ctx.setVariable("shippingAddress", event.shippingAddress());
        ctx.setVariable("items",           event.items());
        ctx.setVariable("totalAmount",     event.totalAmount());

        String subject = "Order Confirmed – #" + shortId(event.orderId());
        String body    = templateEngine.process("email/order-confirmation", ctx);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Order confirmation email sent orderId={}", event.orderId());
    }

    public void sendPaymentReceipt(PaymentProcessedEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderId",   event.orderId());
        ctx.setVariable("paymentId", event.paymentId());
        ctx.setVariable("amount",    event.amount());
        ctx.setVariable("currency",  event.currency());
        ctx.setVariable("gateway",   event.gateway());

        String subject = "Payment Received – Order #" + shortId(event.orderId());
        String body    = templateEngine.process("email/payment-receipt", ctx);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Payment receipt email sent orderId={}", event.orderId());
    }

    public void sendPaymentFailedNotification(PaymentProcessedEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderId",  event.orderId());
        ctx.setVariable("amount",   event.amount());
        ctx.setVariable("currency", event.currency());

        String subject = "Payment Failed – Order #" + shortId(event.orderId());
        String body    = templateEngine.process("email/payment-failed", ctx);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Payment failed email sent orderId={}", event.orderId());
    }

    public void sendShippingUpdate(OrderStatusUpdatedEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderId",        event.orderId());
        ctx.setVariable("previousStatus", event.previousStatus());
        ctx.setVariable("newStatus",      event.newStatus());

        String subject = "Order Update – #" + shortId(event.orderId());
        String body    = templateEngine.process("email/shipping-update", ctx);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Shipping update email sent orderId={}", event.orderId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void sendEmail(String toAddress, String subject, String htmlBody) {
        Mail mail = new Mail(
                new Email(fromEmail, "E-Commerce Platform"),
                subject,
                new Email(toAddress),
                new Content("text/html", htmlBody)
        );

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        try {
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid email failed status={}", response.getStatusCode());
                throw new NotificationException(
                        "Email delivery failed with status: " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("SendGrid request IO error: {}", e.getMessage());
            throw new NotificationException("Email delivery failed", e);
        }
    }

    /**
     * Derives a placeholder email address from a userId.
     * In a production system this would query the User Service for the real address.
     */
    private String deriveEmailFromUserId(String userId) {
        return userId + "@users.ecommerce.local";
    }

    /** Returns the first 8 uppercase characters of a UUID string for display. */
    private String shortId(String id) {
        return id.length() >= 8 ? id.substring(0, 8).toUpperCase() : id.toUpperCase();
    }
}
