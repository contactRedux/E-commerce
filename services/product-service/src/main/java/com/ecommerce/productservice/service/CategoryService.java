package com.ecommerce.productservice.service;

import com.ecommerce.productservice.document.Category;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.dto.CreateCategoryRequest;
import com.ecommerce.productservice.dto.UpdateCategoryRequest;
import com.ecommerce.productservice.exception.CategoryNotFoundException;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .parentCategoryId(request.parentCategoryId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toResponse(categoryRepository.save(category));
    }

    public CategoryResponse getById(String id) {
        return toResponse(findById(id));
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CategoryResponse updateCategory(String id, UpdateCategoryRequest request) {
        Category category = findById(id);
        if (request.name() != null) category.setName(request.name());
        if (request.description() != null) category.setDescription(request.description());
        if (request.parentCategoryId() != null) category.setParentCategoryId(request.parentCategoryId());
        category.setUpdatedAt(LocalDateTime.now());
        return toResponse(categoryRepository.save(category));
    }

    public void deleteCategory(String id) {
        Category category = findById(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException(
                    "Cannot delete category '" + id + "': products still reference it");
        }
        categoryRepository.delete(category);
    }

    private Category findById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getParentCategoryId(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
