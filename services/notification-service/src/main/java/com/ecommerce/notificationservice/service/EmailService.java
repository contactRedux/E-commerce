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

import java.io.IOException;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final SendGrid sendGrid;
    private final String fromEmail;

    public EmailService(SendGrid sendGrid,
                        @Value("${notification.from-email}") String fromEmail) {
        this.sendGrid = sendGrid;
        this.fromEmail = fromEmail;
    }

    public void sendOrderConfirmation(OrderPlacedEvent event) {
        String subject = "Order Confirmed - #" + event.orderId().substring(0, 8).toUpperCase();
        String body = buildOrderConfirmationHtml(event);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Order confirmation email queued for orderId={}", event.orderId());
    }

    public void sendPaymentReceipt(PaymentProcessedEvent event) {
        String subject = "Payment Received - Order #" + event.orderId().substring(0, 8).toUpperCase();
        String body = buildPaymentReceiptHtml(event);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Payment receipt email queued for orderId={}", event.orderId());
    }

    public void sendPaymentFailedNotification(PaymentProcessedEvent event) {
        String subject = "Payment Failed - Order #" + event.orderId().substring(0, 8).toUpperCase();
        String body = buildPaymentFailedHtml(event);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Payment failed email queued for orderId={}", event.orderId());
    }

    public void sendShippingUpdate(OrderStatusUpdatedEvent event) {
        String subject = "Order Update - #" + event.orderId().substring(0, 8).toUpperCase();
        String body = buildShippingUpdateHtml(event);
        sendEmail(deriveEmailFromUserId(event.userId()), subject, body);
        log.info("Shipping update email queued for orderId={}", event.orderId());
    }

    // ---------- private helpers ----------

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
                log.error("SendGrid email failed with status={}", response.getStatusCode());
                throw new NotificationException("Email delivery failed with status: " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("SendGrid request failed: {}", e.getMessage());
            throw new NotificationException("Email delivery failed", e);
        }
    }

    private String buildOrderConfirmationHtml(OrderPlacedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Thank you for your order!</h2>");
        sb.append("<p>Order ID: <strong>").append(event.orderId()).append("</strong></p>");
        sb.append("<p>Shipping to: ").append(event.shippingAddress()).append("</p>");
        sb.append("<table border='1' cellpadding='6' cellspacing='0'>");
        sb.append("<tr><th>Product</th><th>Qty</th><th>Unit Price</th></tr>");
        if (event.items() != null) {
            for (var item : event.items()) {
                sb.append("<tr>")
                  .append("<td>").append(item.productName()).append("</td>")
                  .append("<td>").append(item.quantity()).append("</td>")
                  .append("<td>$").append(item.unitPrice()).append("</td>")
                  .append("</tr>");
            }
        }
        sb.append("</table>");
        sb.append("<p><strong>Total: $").append(event.totalAmount()).append("</strong></p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String buildPaymentReceiptHtml(PaymentProcessedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Payment Received</h2>");
        sb.append("<p>Order ID: <strong>").append(event.orderId()).append("</strong></p>");
        sb.append("<p>Amount: <strong>").append(event.currency()).append(" ").append(event.amount()).append("</strong></p>");
        sb.append("<p>Payment ID: ").append(event.paymentId()).append("</p>");
        sb.append("<p>Gateway: ").append(event.gateway()).append("</p>");
        sb.append("<p>Your payment has been successfully processed.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String buildPaymentFailedHtml(PaymentProcessedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Payment Failed</h2>");
        sb.append("<p>Order ID: <strong>").append(event.orderId()).append("</strong></p>");
        sb.append("<p>Unfortunately your payment of <strong>")
          .append(event.currency()).append(" ").append(event.amount())
          .append("</strong> could not be processed.</p>");
        sb.append("<p>Please update your payment details and try again.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String buildShippingUpdateHtml(OrderStatusUpdatedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Order Status Update</h2>");
        sb.append("<p>Order ID: <strong>").append(event.orderId()).append("</strong></p>");
        sb.append("<p>Status changed from <strong>").append(event.previousStatus())
          .append("</strong> to <strong>").append(event.newStatus()).append("</strong>.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Derives a placeholder email address from the userId.
     * In production this would call the User Service via the API Gateway.
     */
    private String deriveEmailFromUserId(String userId) {
        return userId + "@users.ecommerce.local";
    }
}
