package com.ecommerce.productservice.service;

import com.ecommerce.productservice.document.Category;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.dto.CreateCategoryRequest;
import com.ecommerce.productservice.dto.UpdateCategoryRequest;
import com.ecommerce.productservice.exception.CategoryNotFoundException;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static final String CATEGORY_ID = "cat-123";

    private Category sampleCategory() {
        return Category.builder()
                .id(CATEGORY_ID)
                .name("Electronics")
                .description("Electronic items")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── createCategory ──────────────────────────────────────────────────────────

    @Test
    void createCategory_success() {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "Electronic items", null);

        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(CATEGORY_ID);
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Electronics");
        verify(categoryRepository).save(any(Category.class));
    }

    // ─── getById ─────────────────────────────────────────────────────────────────

    @Test
    void getCategory_notFound_throwsCategoryNotFoundException() {
        when(categoryRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById("bad-id"))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void getCategory_success() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(sampleCategory()));

        CategoryResponse response = categoryService.getById(CATEGORY_ID);

        assertThat(response.id()).isEqualTo(CATEGORY_ID);
        assertThat(response.name()).isEqualTo("Electronics");
    }

    // ─── getAll ──────────────────────────────────────────────────────────────────

    @Test
    void getAllCategories_success() {
        when(categoryRepository.findAll()).thenReturn(List.of(sampleCategory()));

        List<CategoryResponse> result = categoryService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Electronics");
    }

    // ─── updateCategory ──────────────────────────────────────────────────────────

    @Test
    void updateCategory_success() {
        Category category = sampleCategory();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.updateCategory(
                CATEGORY_ID, new UpdateCategoryRequest("Updated Name", null, null));

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.description()).isEqualTo("Electronic items"); // unchanged
    }

    // ─── deleteCategory ──────────────────────────────────────────────────────────

    @Test
    void deleteCategory_success() {
        Category category = sampleCategory();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(false);

        categoryService.deleteCategory(CATEGORY_ID);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_withProducts_throwsIllegalArgumentException() {
        Category category = sampleCategory();
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(CATEGORY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("products still reference it");
        verify(categoryRepository, never()).delete(any());
    }
}
