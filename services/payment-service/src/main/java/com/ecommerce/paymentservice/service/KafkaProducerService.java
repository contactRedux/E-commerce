package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String TOPIC = "payment.processed";

    private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentProcessedEvent for orderId={}", event.orderId(), ex);
                    } else {
                        log.info("Published PaymentProcessedEvent orderId={} status={} gateway={}",
                                event.orderId(), event.status(), event.gateway());
                    }
                });
    }
}
