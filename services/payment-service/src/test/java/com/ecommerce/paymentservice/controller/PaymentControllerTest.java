package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.config.SecurityConfig;
import com.ecommerce.paymentservice.dto.ApiResponse;
import com.ecommerce.paymentservice.dto.CreatePaymentIntentRequest;
import com.ecommerce.paymentservice.dto.PaymentIntentResponse;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.enums.PaymentGateway;
import com.ecommerce.paymentservice.enums.PaymentStatus;
import com.ecommerce.paymentservice.exception.GlobalExceptionHandler;
import com.ecommerce.paymentservice.filter.JwtAuthenticationFilter;
import com.ecommerce.paymentservice.service.PaymentService;
import com.ecommerce.paymentservice.service.StripeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAplaceholder",
        "stripe.api-key=sk_test_placeholder",
        "stripe.webhook-secret=whsec_placeholder"
})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private StripeService stripeService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ── POST /payments/stripe/intent ───────────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1")
    void createStripeIntent_withValidJwt_returns201() throws Exception {
        CreatePaymentIntentRequest req = new CreatePaymentIntentRequest(
                "order-1", "user-1", new BigDecimal("49.99"), "USD", "idem-1");

        PaymentIntentResponse resp = new PaymentIntentResponse("pi_123", "pi_123_secret", "PENDING");
        when(paymentService.createStripePayment(any())).thenReturn(resp);

        mockMvc.perform(post("/payments/stripe/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.gatewayPaymentId").value("pi_123"));
    }

    @Test
    void createStripeIntent_withoutJwt_returns401() throws Exception {
        CreatePaymentIntentRequest req = new CreatePaymentIntentRequest(
                "order-1", "user-1", new BigDecimal("49.99"), "USD", "idem-1");

        mockMvc.perform(post("/payments/stripe/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /payments/webhook/stripe ─────────────────────────────────────────

    @Test
    void stripeWebhook_withoutJwt_returns200() throws Exception {
        doNothing().when(stripeService).handleWebhookEvent(anyString(), anyString());

        mockMvc.perform(post("/payments/webhook/stripe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("Stripe-Signature", "t=1,v1=test")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // ── POST /payments/{id}/refund ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1")
    void refund_withValidJwt_returns200() throws Exception {
        UUID paymentId = UUID.randomUUID();
        doNothing().when(paymentService).refund(paymentId);

        mockMvc.perform(post("/payments/{id}/refund", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // ── GET /payments/order/{orderId} ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "user-1")
    void getByOrderId_withValidJwt_returns200() throws Exception {
        PaymentResponse resp = new PaymentResponse(
                UUID.randomUUID(), "order-1", "STRIPE", "pi_123", null,
                new BigDecimal("49.99"), "USD", "SUCCEEDED", null,
                LocalDateTime.now(), LocalDateTime.now());
        when(paymentService.getPaymentByOrderId("order-1")).thenReturn(resp);

        mockMvc.perform(get("/payments/order/{orderId}", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.orderId").value("order-1"))
                .andExpect(jsonPath("$.data.gatewayClientSecret").doesNotExist());
    }
}
