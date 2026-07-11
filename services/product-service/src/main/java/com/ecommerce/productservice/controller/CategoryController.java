package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.ApiResponse;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.dto.CreateCategoryRequest;
import com.ecommerce.productservice.dto.UpdateCategoryRequest;
import com.ecommerce.productservice.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ApiResponse.success(categoryService.createCategory(request));
    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.success(categoryService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategory(@PathVariable String id) {
        return ApiResponse.success(categoryService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ApiResponse.success(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
    public void deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
    }
}
