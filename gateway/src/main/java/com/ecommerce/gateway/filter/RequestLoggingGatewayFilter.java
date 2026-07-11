package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that assigns a unique {@code X-Request-Id} to every inbound
 * request and emits a structured log line (method, path, status, duration)
 * when the response completes.
 *
 * <p>Runs at order {@code -2}, before the JWT filter (order {@code -1}),
 * so the request ID is available to all downstream filters and services.
 */
@Component
@Slf4j
public class RequestLoggingGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        // Propagate a stable request ID so downstream services can correlate logs
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header("X-Request-Id", requestId)
            .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange)
            .doFinally(signalType -> {
                long duration = System.currentTimeMillis() - startTime;
                // Structured fields keep log parsing simple for Logstash / ELK
                log.info("method={} path={} status={} requestId={} durationMs={}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    mutatedExchange.getResponse().getStatusCode(),
                    requestId,
                    duration);
            });
    }

    /** Runs before the JWT filter so that request ID is set first. */
    @Override
    public int getOrder() {
        return -2;
    }
}
