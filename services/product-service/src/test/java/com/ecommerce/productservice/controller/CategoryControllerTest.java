package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.config.SecurityConfig;
import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.exception.GlobalExceptionHandler;
import com.ecommerce.productservice.filter.JwtAuthenticationFilter;
import com.ecommerce.productservice.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    private static final String CATEGORY_ID = "cat-001";

    private CategoryResponse sampleCategoryResponse() {
        return new CategoryResponse(
                CATEGORY_ID,
                "Electronics",
                "Electronic items",
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    // ─── GET /categories ─────────────────────────────────────────────────────────

    @Test
    void getCategories_public_returns200() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of(sampleCategoryResponse()));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Electronics"));
    }

    // ─── GET /categories/{id} ────────────────────────────────────────────────────

    @Test
    void getCategory_public_returns200() throws Exception {
        when(categoryService.getById(CATEGORY_ID)).thenReturn(sampleCategoryResponse());

        mockMvc.perform(get("/categories/{id}", CATEGORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(CATEGORY_ID));
    }

    // ─── POST /categories ────────────────────────────────────────────────────────

    @Test
    void createCategory_noAuth_returns401() throws Exception {
        CreateCategoryRequest body = new CreateCategoryRequest("Electronics", "desc", null);

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_asAdmin_returns201() throws Exception {
        CreateCategoryRequest body = new CreateCategoryRequest("Electronics", "desc", null);
        when(categoryService.createCategory(any(CreateCategoryRequest.class)))
                .thenReturn(sampleCategoryResponse());

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("Electronics"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_blankName_returns400() throws Exception {
        CreateCategoryRequest body = new CreateCategoryRequest("", null, null);

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
