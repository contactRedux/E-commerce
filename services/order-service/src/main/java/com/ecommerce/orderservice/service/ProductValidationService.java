package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.OrderItemDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Validates that ordered products exist and have sufficient stock by calling the
 * Product Service via the API Gateway. Uses Resilience4j circuit breaker and retry
 * for fault-tolerance. Falls back gracefully (allows the order) when the Product
 * Service is unavailable, so a transient outage never blocks the checkout flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductValidationService {

    private final WebClient webClient;

    /**
     * Validates each item in the order has sufficient stock.
     *
     * @param items       list of order items to validate
     * @param bearerToken JWT Bearer token forwarded from the incoming request
     * @return {@code true} if all products are valid and in stock; {@code false} otherwise
     */
    @CircuitBreaker(name = "productService", fallbackMethod = "validateFallback")
    @Retry(name = "productService")
    public boolean validateProductsAndStock(List<OrderItemDto> items, String bearerToken) {
        log.info("Validating {} product(s) via Product Service", items.size());

        for (OrderItemDto item : items) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.get()
                        .uri("/products/{id}", item.productId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response == null) {
                    log.warn("Null response for product={} during validation", item.productId());
                    return false;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    Number stock = (Number) data.get("stockQuantity");
                    if (stock != null && stock.intValue() < item.quantity()) {
                        log.warn("Insufficient stock: product={} required={} available={}",
                                item.productId(), item.quantity(), stock.intValue());
                        return false;
                    }
                }
            } catch (WebClientResponseException.NotFound e) {
                log.warn("Product not found during validation: productId={}", item.productId());
                return false;
            }
        }
        return true;
    }

    /**
     * Fallback invoked when the productService circuit breaker is OPEN or a
     * non-retryable exception is thrown. Allows the order to proceed (graceful
     * degradation) so a downstream outage never blocks checkout.
     */
    public boolean validateFallback(List<OrderItemDto> items, String bearerToken, Throwable ex) {
        log.warn("ProductValidationService circuit open — skipping validation. reason={}",
                ex.getClass().getSimpleName());
        return true;
    }
}
