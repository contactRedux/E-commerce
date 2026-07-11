package com.ecommerce.productservice.service;

import com.ecommerce.productservice.document.Category;
import com.ecommerce.productservice.document.Product;
import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.exception.CategoryNotFoundException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private static final String PRODUCT_ID = "prod-123";
    private static final String CATEGORY_ID = "cat-456";

    private Product sampleProduct() {
        return Product.builder()
                .id(PRODUCT_ID)
                .name("Test Product")
                .description("A test product")
                .price(new BigDecimal("29.99"))
                .categoryId(CATEGORY_ID)
                .stockQuantity(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Category sampleCategory() {
        return Category.builder()
                .id(CATEGORY_ID)
                .name("Electronics")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── createProduct ─────────────────────────────────────────────────────────

    @Test
    void createProduct_success() {
        CreateProductRequest request = new CreateProductRequest(
                "Test Product", "desc", new BigDecimal("29.99"), CATEGORY_ID, 10, null, null);

        when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(PRODUCT_ID);
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Product");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("29.99"));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_invalidCategoryId_throwsCategoryNotFoundException() {
        CreateProductRequest request = new CreateProductRequest(
                "Test", null, new BigDecimal("10.00"), "invalid-cat", 0, null, null);

        when(categoryRepository.existsById("invalid-cat")).thenReturn(false);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    // ─── getProduct ─────────────────────────────────────────────────────────────

    @Test
    void getProduct_notFound_throwsProductNotFoundException() {
        when(productRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct("bad-id"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProduct_success() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct()));

        ProductResponse response = productService.getProduct(PRODUCT_ID);

        assertThat(response.id()).isEqualTo(PRODUCT_ID);
        assertThat(response.name()).isEqualTo("Test Product");
    }

    // ─── searchProducts ─────────────────────────────────────────────────────────

    @Test
    void searchProducts_byName_returnsPagedResult() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), pageable, 1);
        when(productRepository.findByNameContainingIgnoreCase("Test", pageable)).thenReturn(page);

        PagedResponse<ProductResponse> result = productService.searchProducts("Test", null, null, pageable);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).name()).isEqualTo("Test Product");
    }

    @Test
    void searchProducts_byCategoryId_returnsPagedResult() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), pageable, 1);
        when(productRepository.findByCategoryId(CATEGORY_ID, pageable)).thenReturn(page);

        PagedResponse<ProductResponse> result = productService.searchProducts(null, CATEGORY_ID, null, pageable);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).categoryId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void searchProducts_noFilters_returnsAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(sampleProduct()), pageable, 1);
        when(productRepository.findAll(pageable)).thenReturn(page);

        PagedResponse<ProductResponse> result = productService.searchProducts(null, null, null, pageable);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
    }

    // ─── updateStock ────────────────────────────────────────────────────────────

    @Test
    void updateStock_success_decrement() {
        Product product = sampleProduct(); // stockQuantity = 10
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.updateStock(PRODUCT_ID, new StockUpdateRequest(-3));

        assertThat(response.stockQuantity()).isEqualTo(7);
    }

    @Test
    void updateStock_insufficientStock_throwsIllegalArgumentException() {
        Product product = sampleProduct(); // stockQuantity = 10
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateStock(PRODUCT_ID, new StockUpdateRequest(-20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateStock_restock_success() {
        Product product = sampleProduct(); // stockQuantity = 10
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.updateStock(PRODUCT_ID, new StockUpdateRequest(5));

        assertThat(response.stockQuantity()).isEqualTo(15);
    }

    // ─── deleteProduct ───────────────────────────────────────────────────────────

    @Test
    void deleteProduct_success() {
        Product product = sampleProduct();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        productService.deleteProduct(PRODUCT_ID);

        verify(productRepository).delete(product);
    }

    @Test
    void deleteProduct_notFound_throwsProductNotFoundException() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
