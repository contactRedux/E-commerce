package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Configures Redis-backed rate limiting for the gateway.
 *
 * <p>Rate limits:
 * <ul>
 *   <li>{@code replenishRate} = 10 tokens/second per key (sustained throughput).</li>
 *   <li>{@code burstCapacity} = 20 tokens (allows short bursts above sustained rate).</li>
 * </ul>
 *
 * <p>The key resolver uses the {@code X-User-Id} header set by
 * {@link com.ecommerce.gateway.filter.JwtAuthenticationGatewayFilter} for
 * authenticated requests; unauthenticated requests fall back to IP address.
 * This ensures rate limits are per-user rather than per-IP for logged-in users,
 * preventing a shared NAT from triggering limits for all users behind it.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // replenishRate: sustained tokens/second; burstCapacity: max tokens per burst window
        return new RedisRateLimiter(10, 20);
    }

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            // Fallback: key by remote IP for unauthenticated (public) endpoints
            return Mono.just(
                Objects.requireNonNull(
                    exchange.getRequest().getRemoteAddress(),
                    "Remote address must not be null"
                ).getAddress().getHostAddress()
            );
        };
    }
}
