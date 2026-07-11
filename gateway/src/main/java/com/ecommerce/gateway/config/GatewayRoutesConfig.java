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
 *   <li>Matches on a path prefix.</li>
 *   <li>Applies Redis-backed rate limiting keyed by user ID or IP.</li>
 *   <li>Forwards to the appropriate service using Docker internal hostnames
 *       with port overridable via env vars.</li>
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

            // ── User Service ─────────────────────────────────────────────────
            .route("user-service-auth", r -> r
                .path("/auth/**")
                .filters(f -> f
                    .rewritePath("/auth/(?<remaining>.*)", "/auth/${remaining}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://user-service:${USER_SERVICE_PORT:8081}"))

            .route("user-service-users", r -> r
                .path("/users/**")
                .filters(f -> f
                    .rewritePath("/users/(?<remaining>.*)", "/users/${remaining}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://user-service:${USER_SERVICE_PORT:8081}"))

            // ── Product Catalog Service ───────────────────────────────────────
            .route("product-service", r -> r
                .path("/products/**", "/categories/**")
                .filters(f -> f
                    .requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://product-service:${PRODUCT_SERVICE_PORT:8082}"))

            // ── Shopping Cart Service ─────────────────────────────────────────
            .route("cart-service", r -> r
                .path("/cart/**")
                .filters(f -> f
                    .requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://cart-service:${CART_SERVICE_PORT:8083}"))

            // ── Order Service ─────────────────────────────────────────────────
            .route("order-service", r -> r
                .path("/orders/**")
                .filters(f -> f
                    .requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://order-service:${ORDER_SERVICE_PORT:8084}"))

            // ── Payment Service ───────────────────────────────────────────────
            .route("payment-service", r -> r
                .path("/payments/**")
                .filters(f -> f
                    .requestRateLimiter(config -> config
                        .setRateLimiter(rateLimiter)
                        .setKeyResolver(userKeyResolver)))
                .uri("http://payment-service:${PAYMENT_SERVICE_PORT:8085}"))

            .build();
    }
}
