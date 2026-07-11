package com.ecommerce.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Handles circuit-breaker fallback responses for all gateway routes.
 * When a downstream service is unavailable and the circuit breaker trips,
 * the gateway forwards to {@code /fallback} and this controller returns a
 * consistent JSON envelope with HTTP 503.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> fallback() {
        return Mono.just(Map.of(
                "status",  "error",
                "data",    (Object) null,
                "message", "Service temporarily unavailable. Please try again shortly."
        ));
    }
}
