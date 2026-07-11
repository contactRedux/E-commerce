package com.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * CORS configuration for the API Gateway.
 *
 * <p>Allowed origins are read from the {@code ALLOWED_ORIGINS} environment variable
 * (comma-separated). The default ({@code http://localhost:3000}) covers the local
 * frontend dev server.
 *
 * <p>The Spring Cloud Gateway global CORS settings in {@code application.yml} handle
 * de-duplication of CORS response headers added by both the gateway and downstream
 * services. This bean provides fine-grained programmatic control.
 */
@Configuration
public class CorsConfig {

    @Value("${allowed.origins:http://localhost:3000}")
    private String allowedOriginsStr;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        Arrays.stream(allowedOriginsStr.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .forEach(config::addAllowedOrigin);

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
