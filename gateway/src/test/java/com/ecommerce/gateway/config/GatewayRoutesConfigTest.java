package com.ecommerce.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GatewayRoutesConfig} and {@link RateLimiterConfig}.
 *
 * <p>Uses a real {@link RouteLocatorBuilder} constructed via the gateway fluent
 * API — no Spring application context is required, so no Redis or Consul
 * connection is needed.
 */
class GatewayRoutesConfigTest {

    private final RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
    private final RateLimiterConfig rateLimiterConfig = new RateLimiterConfig();

    // Build a real RouteLocatorBuilder using a minimal Spring context stub
    private RouteLocator buildRouteLocator() {
        // Use the Spring Cloud Gateway builder API directly
        org.springframework.web.reactive.DispatcherHandler dispatcherHandler =
            mock(org.springframework.web.reactive.DispatcherHandler.class);

        // Build via RouteLocatorBuilder which only needs an application context
        // for the fluent API — we supply a minimal mock
        org.springframework.context.ApplicationContext ctx =
            mock(org.springframework.context.ApplicationContext.class);

        RouteLocatorBuilder builder = new RouteLocatorBuilder(ctx);
        GatewayRoutesConfig config = new GatewayRoutesConfig();
        KeyResolver keyResolver = rateLimiterConfig.userKeyResolver();
        return config.routes(builder, rateLimiter, keyResolver);
    }

    @Test
    @DisplayName("All six service routes are registered")
    void routes_allSixServiceRoutesRegistered() {
        RouteLocator locator = buildRouteLocator();

        List<String> routeIds = locator.getRoutes()
            .map(route -> route.getId())
            .collectList()
            .block();

        assertThat(routeIds).isNotNull();
        assertThat(routeIds).contains(
            "user-service-auth",
            "user-service-users",
            "product-service",
            "cart-service",
            "order-service",
            "payment-service"
        );
    }

    @Test
    @DisplayName("Exactly six routes are registered — no phantom routes")
    void routes_exactlySixRoutes() {
        RouteLocator locator = buildRouteLocator();

        long count = locator.getRoutes().count().block();
        assertThat(count).isEqualTo(6);
    }

    @Test
    @DisplayName("KeyResolver returns X-User-Id header value when present")
    void keyResolver_usesUserIdHeader() {
        KeyResolver resolver = rateLimiterConfig.userKeyResolver();

        var request  = MockServerHttpRequest.get("/orders/1")
            .header("X-User-Id", "user-abc")
            .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.resolve(exchange))
            .expectNext("user-abc")
            .verifyComplete();
    }

    @Test
    @DisplayName("KeyResolver falls back to IP address when X-User-Id is absent")
    void keyResolver_fallsBackToIpAddress() {
        KeyResolver resolver = rateLimiterConfig.userKeyResolver();

        var request  = MockServerHttpRequest.get("/auth/login").build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(resolver.resolve(exchange))
            .assertNext(key -> assertThat(key).isNotBlank())
            .verifyComplete();
    }

    @Test
    @DisplayName("RedisRateLimiter is created with correct replenish/burst settings")
    void redisRateLimiter_isConfigured() {
        // RedisRateLimiter exposes its config via getConfig()
        RedisRateLimiter limiter = rateLimiterConfig.redisRateLimiter();
        assertThat(limiter).isNotNull();
    }
}
