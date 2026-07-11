package com.ecommerce.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(@Value("${gateway.base-url:http://gateway:8080}") String gatewayBaseUrl) {
        return WebClient.builder()
                .baseUrl(gatewayBaseUrl)
                .build();
    }
}
