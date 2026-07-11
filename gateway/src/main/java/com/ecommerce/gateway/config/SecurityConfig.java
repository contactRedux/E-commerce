package com.ecommerce.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * WebFlux Security configuration for the gateway.
 *
 * <p>Spring Security is intentionally set to permit-all here because JWT
 * authentication is handled upstream by
 * {@link com.ecommerce.gateway.filter.JwtAuthenticationGatewayFilter}, a
 * {@link org.springframework.cloud.gateway.filter.GlobalFilter} that runs
 * before routing. Each downstream service also independently validates the
 * forwarded {@code X-User-Id} / {@code X-User-Role} headers.
 *
 * <p>CSRF is disabled because the gateway is a stateless API proxy that does
 * not issue session cookies, and CORS is handled by
 * {@link com.ecommerce.gateway.config.CorsConfig}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            )
            .build();
    }
}
