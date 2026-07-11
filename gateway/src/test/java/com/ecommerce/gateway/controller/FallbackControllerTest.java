package com.ecommerce.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Slice tests for {@link FallbackController}.
 *
 * <p>Verifies that the circuit-breaker fallback endpoint returns HTTP 503 with
 * the standard JSON error envelope used across all services.
 */
@WebFluxTest(FallbackController.class)
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void fallback_returns503ServiceUnavailable() {
        webTestClient.get()
                .uri("/fallback")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void fallback_returnsStandardErrorEnvelope() {
        webTestClient.get()
                .uri("/fallback")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("error")
                .jsonPath("$.message").isEqualTo(
                        "Service temporarily unavailable. Please try again shortly.")
                .jsonPath("$.data").isEmpty();
    }
}
