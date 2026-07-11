package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.OrderStatusUpdatedEvent;
import com.ecommerce.notificationservice.exception.NotificationException;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /**
     * Twilio magic test number used in sandbox / test mode.
     * In production, retrieve the recipient phone from the User Service.
     */
    private static final String SANDBOX_TO_NUMBER = "+15005550006";

    private final String fromNumber;

    public SmsService(@Qualifier("twilioFromNumber") String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public void sendOrderStatusSms(OrderStatusUpdatedEvent event) {
        String body = buildSmsBody(event);
        try {
            Message message = Message.creator(
                    new PhoneNumber(SANDBOX_TO_NUMBER),
                    new PhoneNumber(fromNumber),
                    body
            ).create();
            log.info("SMS sent successfully, SID={}", message.getSid());
        } catch (ApiException e) {
            // Log message only — never log auth token or phone numbers (PII/security)
            log.error("Twilio SMS delivery failed: {}", e.getMessage());
            throw new NotificationException("SMS delivery failed: " + e.getMessage());
        }
    }

    private String buildSmsBody(OrderStatusUpdatedEvent event) {
        return String.format("Your order #%s status: %s → %s",
                event.orderId().substring(0, 8).toUpperCase(),
                event.previousStatus(),
                event.newStatus());
    }
}
