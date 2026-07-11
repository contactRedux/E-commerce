package com.ecommerce.notificationservice.config;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration that disables Consul auto-configuration and provides
 * configuration overrides needed for unit tests.
 *
 * The unit tests in this project use Mockito directly and do not require
 * a live Kafka broker.
 *
 * For integration test usage, annotate the test class with:
 * {@code @EmbeddedKafka(partitions = 1, topics = {"order.placed", "payment.processed", "order.status.updated"})}
 * and inject {@code EmbeddedKafkaBroker} to obtain the bootstrap servers address.
 */
@TestConfiguration
public class TestKafkaConsumerConfig {
    // No bean definitions required for current Mockito-based unit test suite.
    // Extend this class to configure embedded Kafka for future integration tests.
}
