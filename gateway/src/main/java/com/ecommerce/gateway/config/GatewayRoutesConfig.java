package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions for all six downstream services.
 *
 * <p>Each route:
 * <ul>
 *   <li>Matches on a versioned path prefix ({@code /v1/...}).</li>
 *   <li>Applies Redis-backed rate limiting keyed by user ID or IP.</li>
 *   <li>Applies a Resilience4j circuit breaker that redirects to {@code /fallback}
 *       when the downstream service is unavailable.</li>
 *   <li>Forwards to the appropriate service using Docker internal hostnames
 *       with ports overridable via environment variables.</li>
 * </ul>
 *
 * <p>The {@code Authorization} header is stripped by
 * {@link com.ecommerce.gateway.filter.JwtAuthenticationGatewayFilter} before
 * routing, so the raw token never reaches a downstream service.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               RedisRateLimiter rateLimiter,
                               KeyResolver userKeyResolver) {
        return builder.routes()

            // ── User Service — auth ───────────────────────────────────────────
            .route("user-service-auth", r -> r
                .path("/v1/auth/**")
                .filters(f -> f
                    .circuitBreaker(cb -> cb.setName("default-cb").setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(cfg -> cfg
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://user-service:${USER_SERVICE_PORT:8081}"))

            // ── User Service — users ──────────────────────────────────────────
            .route("user-service-users", r -> r
                .path("/v1/users/**")
                .filters(f -> f
                    .circuitBreaker(cb -> cb.setName("default-cb").setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(cfg -> cfg
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://user-service:${USER_SERVICE_PORT:8081}"))

            // ── Product Catalog Service ───────────────────────────────────────
            .route("product-service", r -> r
                .path("/v1/products/**", "/v1/categories/**")
                .filters(f -> f
                    .circuitBreaker(cb -> cb.setName("default-cb").setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(cfg -> cfg
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://product-service:${PRODUCT_SERVICE_PORT:8082}"))

            // ── Shopping Cart Service ─────────────────────────────────────────
            .route("cart-service", r -> r
                .path("/v1/cart/**")
                .filters(f -> f
                    .circuitBreaker(cb -> cb.setName("default-cb").setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(cfg -> cfg
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://cart-service:${CART_SERVICE_PORT:8083}"))

            // ── Order Service ─────────────────────────────────────────────────
            .route("order-service", r -> r
                .path("/v1/orders/**")
                .filters(f -> f
                    .circuitBreaker(cb -> cb.setName("default-cb").setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(cfg -> cfg
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://order-service:${ORDER_SERVICE_PORT:8084}"))

            // ── Payment Service ───────────────────────────────────────────────
            .route("payment-service", r -> r
                .path("/v1/payments/**")
                .filters(f -> f
                    .circuitBreaker(cb -> cb.setName("default-cb").setFallbackUri("forward:/fallback"))
                    .requestRateLimiter(cfg -> cfg
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://payment-service:${PAYMENT_SERVICE_PORT:8085}"))

            .build();
    }
}
