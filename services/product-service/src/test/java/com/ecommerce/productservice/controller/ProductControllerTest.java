package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.config.SecurityConfig;
import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.exception.GlobalExceptionHandler;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.filter.JwtAuthenticationFilter;
import com.ecommerce.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private static final String PRODUCT_ID = "prod-001";

    private ProductResponse sampleProductResponse() {
        return new ProductResponse(
                PRODUCT_ID,
                "Test Laptop",
                "A great laptop",
                new BigDecimal("999.99"),
                "cat-001",
                50,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    // ─── GET /products ───────────────────────────────────────────────────────────

    @Test
    void getProducts_public_returns200() throws Exception {
        PagedResponse<ProductResponse> paged = new PagedResponse<>(
                List.of(sampleProductResponse()), 0, 20, 1L);
        when(productService.searchProducts(isNull(), isNull(), isNull(), any()))
                .thenReturn(paged);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    // ─── GET /products/{id} ──────────────────────────────────────────────────────

    @Test
    void getProduct_public_returns200() throws Exception {
        when(productService.getProduct(PRODUCT_ID)).thenReturn(sampleProductResponse());

        mockMvc.perform(get("/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.name").value("Test Laptop"));
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        when(productService.getProduct("bad-id")).thenThrow(new ProductNotFoundException("bad-id"));

        mockMvc.perform(get("/products/{id}", "bad-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ─── POST /products ──────────────────────────────────────────────────────────

    @Test
    void createProduct_noAuth_returns401() throws Exception {
        CreateProductRequest body = new CreateProductRequest(
                "Laptop", "desc", new BigDecimal("999.99"), "cat-001", 10, null, null);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_asAdmin_returns201() throws Exception {
        CreateProductRequest body = new CreateProductRequest(
                "Laptop", "desc", new BigDecimal("999.99"), "cat-001", 10, null, null);
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenReturn(sampleProductResponse());

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_invalidBody_returns400() throws Exception {
        // Missing required 'name'
        String body = "{\"price\": 99.99}";

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ─── PUT /products/{id}/stock ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStock_asAdmin_returns200() throws Exception {
        ProductResponse updated = new ProductResponse(
                PRODUCT_ID, "Test Laptop", "A great laptop",
                new BigDecimal("999.99"), "cat-001", 40, null, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(productService.updateStock(eq(PRODUCT_ID), any(StockUpdateRequest.class))).thenReturn(updated);

        StockUpdateRequest body = new StockUpdateRequest(-10);

        mockMvc.perform(put("/products/{id}/stock", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.stockQuantity").value(40));
    }

    @Test
    void updateStock_noAuth_returns401() throws Exception {
        StockUpdateRequest body = new StockUpdateRequest(-5);

        mockMvc.perform(put("/products/{id}/stock", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
