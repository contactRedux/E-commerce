package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.dto.OrderPlacedEvent;
import com.ecommerce.notificationservice.dto.OrderStatusUpdatedEvent;
import com.ecommerce.notificationservice.dto.PaymentProcessedEvent;
import com.ecommerce.notificationservice.service.EmailService;
import com.ecommerce.notificationservice.service.SmsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

    private final EmailService emailService;
    private final SmsService smsService;
    private final ObjectMapper objectMapper;

    public NotificationKafkaConsumer(EmailService emailService,
                                     SmsService smsService,
                                     ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.placed", groupId = "notification-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderPlaced(String message) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(message, OrderPlacedEvent.class);
            log.info("Processing order.placed event, orderId={}", event.orderId());
            emailService.sendOrderConfirmation(event);
        } catch (JsonProcessingException e) {
            // Log-and-skip: DefaultErrorHandler retries 3x then discards
            log.error("Failed to deserialize order.placed message: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.processed", groupId = "notification-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handlePaymentProcessed(String message) {
        try {
            PaymentProcessedEvent event = objectMapper.readValue(message, PaymentProcessedEvent.class);
            log.info("Processing payment.processed event, orderId={}", event.orderId());
            if ("SUCCEEDED".equals(event.status())) {
                emailService.sendPaymentReceipt(event);
            } else if ("FAILED".equals(event.status())) {
                emailService.sendPaymentFailedNotification(event);
            } else {
                log.info("Ignoring payment.processed event with unhandled status={}", event.status());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize payment.processed message: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "order.status.updated", groupId = "notification-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderStatusUpdated(String message) {
        try {
            OrderStatusUpdatedEvent event = objectMapper.readValue(message, OrderStatusUpdatedEvent.class);
            log.info("Processing order.status.updated event, orderId={}", event.orderId());
            emailService.sendShippingUpdate(event);
            smsService.sendOrderStatusSms(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize order.status.updated message: {}", e.getMessage());
        }
    }
}
