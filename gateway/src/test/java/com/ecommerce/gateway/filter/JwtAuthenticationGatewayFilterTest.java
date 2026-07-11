package com.ecommerce.gateway.filter;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationGatewayFilter}.
 *
 * <p>Uses a generated in-memory RSA key pair so tests are self-contained and
 * never require external config.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationGatewayFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthenticationGatewayFilter filter;
    private KeyPair keyPair;
    private String publicKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        publicKeyPem = Base64.getEncoder().encodeToString(
            keyPair.getPublic().getEncoded());

        filter = new JwtAuthenticationGatewayFilter();
        // Inject public key via the package-private helper used in tests
        setField(filter, "publicKeyPem", publicKeyPem);

        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildToken(String subject, String role, Date expiry) {
        return Jwts.builder()
            .subject(subject)
            .claim("role", role)
            .claim("email", subject + "@example.com")
            .expiration(expiry)
            .signWith(keyPair.getPrivate())
            .compact();
    }

    private String validToken() {
        return buildToken("user-123", "ROLE_CUSTOMER",
            new Date(System.currentTimeMillis() + 60_000));
    }

    private String expiredToken() {
        return buildToken("user-123", "ROLE_CUSTOMER",
            new Date(System.currentTimeMillis() - 60_000));
    }

    /** Reflectively set a private field (avoids making filter fields package-visible). */
    private void setField(Object target, String fieldName, String value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Public path /auth/login bypasses JWT validation")
    void filter_publicPath_skipsValidation() {
        var request  = MockServerHttpRequest.get("/auth/login").build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        // Chain must have been invoked — no 401
        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Request without Authorization header returns 401")
    void filter_missingAuthHeader_returns401() {
        var request  = MockServerHttpRequest.get("/orders/1").build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Request with malformed Bearer token returns 401")
    void filter_invalidToken_returns401() {
        var request  = MockServerHttpRequest.get("/orders/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token")
            .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Expired token returns 401")
    void filter_expiredToken_returns401() {
        var request  = MockServerHttpRequest.get("/orders/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
            .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Valid token sets X-User-Id and X-User-Role headers on downstream request")
    void filter_validToken_setsUserHeaders() {
        var request  = MockServerHttpRequest.get("/orders/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
            .build();
        var exchange = MockServerWebExchange.from(request);

        // Capture the mutated exchange passed to the chain
        var capturedExchange = new MockServerWebExchange[1];
        when(chain.filter(any())).thenAnswer(inv -> {
            capturedExchange[0] = inv.getArgument(0);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        var headers = capturedExchange[0].getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ROLE_CUSTOMER");
        // Authorization header must be stripped — never forwarded downstream
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    @DisplayName("Authorization header is removed from downstream request after validation")
    void filter_validToken_stripsAuthorizationHeader() {
        var request  = MockServerHttpRequest.get("/cart/user-123")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
            .build();
        var exchange = MockServerWebExchange.from(request);

        var capturedExchange = new MockServerWebExchange[1];
        when(chain.filter(any())).thenAnswer(inv -> {
            capturedExchange[0] = inv.getArgument(0);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(capturedExchange[0].getRequest().getHeaders()
            .containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("Filter runs at order -1")
    void filter_hasCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
