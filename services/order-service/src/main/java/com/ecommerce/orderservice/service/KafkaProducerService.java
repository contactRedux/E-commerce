package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.OrderPlacedEvent;
import com.ecommerce.orderservice.dto.OrderStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String TOPIC_ORDER_PLACED         = "order.placed";
    private static final String TOPIC_ORDER_STATUS_UPDATED = "order.status.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing order.placed event for orderId={}", event.orderId());
        kafkaTemplate.send(TOPIC_ORDER_PLACED, event.orderId(), event);
    }

    public void publishOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        log.info("Publishing order.status.updated event for orderId={} newStatus={}",
                event.orderId(), event.newStatus());
        kafkaTemplate.send(TOPIC_ORDER_STATUS_UPDATED, event.orderId(), event);
    }
}
