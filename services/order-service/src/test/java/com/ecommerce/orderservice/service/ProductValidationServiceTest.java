package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductValidationService}.
 *
 * <p>All WebClient interactions are fully mocked — no HTTP calls are made.
 */
@ExtendWith(MockitoExtension.class)
class ProductValidationServiceTest {

    @Mock
    private WebClient webClient;

    private ProductValidationService productValidationService;

    // Fluent WebClient mock chain helpers
    private WebClient.RequestHeadersUriSpec<?> uriSpec;
    private WebClient.RequestHeadersSpec<?> headersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        productValidationService = new ProductValidationService(webClient);

        uriSpec      = mock(WebClient.RequestHeadersUriSpec.class);
        headersSpec  = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateProductsAndStock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void validateProductsAndStock_sufficientStock_returnsTrue() {
        Map<String, Object> responseBody = Map.of(
                "status", "success",
                "data", Map.of("stockQuantity", 10)
        );
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

        List<OrderItemDto> items = List.of(
                new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))
        );

        boolean result = productValidationService.validateProductsAndStock(items, "test-token");

        assertThat(result).isTrue();
    }

    @Test
    void validateProductsAndStock_insufficientStock_returnsFalse() {
        Map<String, Object> responseBody = Map.of(
                "status", "success",
                "data", Map.of("stockQuantity", 1)      // only 1 in stock but 5 requested
        );
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));

        List<OrderItemDto> items = List.of(
                new OrderItemDto("prod-1", "Widget", 5, new BigDecimal("10.00"))
        );

        boolean result = productValidationService.validateProductsAndStock(items, "test-token");

        assertThat(result).isFalse();
    }

    @Test
    void validateProductsAndStock_productNotFound_returnsFalse() {
        WebClientResponseException.NotFound notFound =
                (WebClientResponseException.NotFound) WebClientResponseException.create(
                        404, "Not Found",
                        org.springframework.http.HttpHeaders.EMPTY,
                        null,
                        java.nio.charset.StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(Map.class)).thenThrow(notFound);

        List<OrderItemDto> items = List.of(
                new OrderItemDto("nonexistent-prod", "Ghost", 1, new BigDecimal("5.00"))
        );

        boolean result = productValidationService.validateProductsAndStock(items, "test-token");

        assertThat(result).isFalse();
    }

    @Test
    void validateFallback_always_returnsTrue() {
        List<OrderItemDto> items = List.of(
                new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("10.00"))
        );

        // Invoke the fallback method directly (as Resilience4j would when circuit opens)
        boolean result = productValidationService.validateFallback(
                items, "test-token", new RuntimeException("circuit open"));

        assertThat(result).isTrue();
    }
}
