package com.ecommerce.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

/**
 * Global filter that validates RS256 JWT tokens on every inbound request.
 *
 * <p>Public routes bypass validation entirely. For all other routes the filter:
 * <ol>
 *   <li>Extracts the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Validates the token signature using the configured RSA public key.</li>
 *   <li>Forwards {@code X-User-Id}, {@code X-User-Role}, and {@code X-User-Email}
 *       headers to downstream services.</li>
 *   <li>Strips the original {@code Authorization} header so the raw token is
 *       never forwarded downstream.</li>
 * </ol>
 *
 * <p>The JWT private key lives exclusively in the User Service. The gateway
 * only ever holds the public key — never log or expose token content.
 */
@Component
@Slf4j
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    @Value("${jwt.public-key:}")
    private String publicKeyPem;

    /**
     * Paths that bypass JWT validation. Prefix-matched so {@code /auth/login}
     * matches the {@code /auth} entry, etc.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/v1/auth/register",
        "/v1/auth/login",
        "/v1/auth/refresh",
        "/v1/auth/public-key",
        "/v1/payments/webhook",
        "/fallback",
        "/actuator",
        "/docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = validateToken(token);

            String userId = claims.getSubject();
            String role   = claims.get("role", String.class);
            String email  = claims.get("email", String.class);

            // Build a mutated request: add user-identity headers, strip Authorization
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Strip raw token — downstream services must not receive it
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    if (userId != null) headers.set("X-User-Id",    userId);
                    if (role   != null) headers.set("X-User-Role",  role);
                    if (email  != null) headers.set("X-User-Email", email);
                })
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            // Log only the message — never log the token itself
            log.warn("JWT validation failed: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Parses and validates the JWT using the RS256 public key.
     * Throws {@link JwtException} or {@link IllegalArgumentException} on failure.
     */
    Claims validateToken(String token) {
        PublicKey publicKey = parsePublicKey(publicKeyPem);
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Parses a PEM-encoded RSA public key.
     * The PEM value may include or omit the {@code -----BEGIN PUBLIC KEY-----} headers.
     */
    PublicKey parsePublicKey(String pem) {
        try {
            String cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse RSA public key", e);
        }
    }

    /** Runs before the routing filter (order -1) but after the logging filter (order -2). */
    @Override
    public int getOrder() {
        return -1;
    }
}
