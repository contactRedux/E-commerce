package com.ecommerce.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gateway-level error handler that converts unhandled exceptions into a
 * consistent JSON error envelope before the response is written to the client.
 *
 * <p>Stack traces are never surfaced to the caller — only a generic message
 * is returned. Detailed error information is logged server-side only.
 *
 * <p>Runs at order {@code -1} so it takes precedence over the default
 * Spring Boot {@code DefaultErrorWebExceptionHandler}.
 */
@Component
@Order(-1)
@Slf4j
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final String ERROR_BODY =
        "{\"status\":\"error\",\"data\":null,\"message\":\"An error occurred at the gateway\"}";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Log the real cause server-side — never expose it in the response
        log.error("Gateway error on {} {}: {}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getPath().value(),
            ex.getMessage());

        var response = exchange.getResponse();

        if (ex instanceof ResponseStatusException rse) {
            response.setStatusCode(rse.getStatusCode());
        } else {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = response.bufferFactory()
            .wrap(ERROR_BODY.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
