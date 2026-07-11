package com.ecommerce.productservice.service;

import com.ecommerce.productservice.document.Product;
import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.exception.CategoryNotFoundException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        if (request.categoryId() != null && !request.categoryId().isBlank()) {
            if (!categoryRepository.existsById(request.categoryId())) {
                throw new CategoryNotFoundException(request.categoryId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .categoryId(request.categoryId())
                .stockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0)
                .imageUrl(request.imageUrl())
                .attributes(request.attributes() != null ? request.attributes() : new java.util.HashMap<>())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(productRepository.save(product));
    }

    public ProductResponse getProduct(String id) {
        return toResponse(findById(id));
    }

    public PagedResponse<ProductResponse> searchProducts(
            String name, String categoryId, Boolean inStockOnly, Pageable pageable) {

        Page<Product> page;

        if (Boolean.TRUE.equals(inStockOnly)) {
            // In-stock only: stock > 0; apply secondary filters in memory where needed
            if (categoryId != null && name != null) {
                page = productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, name, pageable);
                var filtered = page.getContent().stream()
                        .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0)
                        .toList();
                return new PagedResponse<>(filtered.stream().map(this::toResponse).toList(),
                        page.getNumber(), page.getSize(), filtered.size());
            } else if (categoryId != null) {
                page = productRepository.findByCategoryId(categoryId, pageable);
                var filtered = page.getContent().stream()
                        .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0)
                        .toList();
                return new PagedResponse<>(filtered.stream().map(this::toResponse).toList(),
                        page.getNumber(), page.getSize(), filtered.size());
            } else if (name != null) {
                page = productRepository.findByNameContainingIgnoreCase(name, pageable);
                var filtered = page.getContent().stream()
                        .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() > 0)
                        .toList();
                return new PagedResponse<>(filtered.stream().map(this::toResponse).toList(),
                        page.getNumber(), page.getSize(), filtered.size());
            } else {
                page = productRepository.findByStockQuantityGreaterThan(0, pageable);
            }
        } else if (categoryId != null && name != null) {
            page = productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, name, pageable);
        } else if (categoryId != null) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else if (name != null) {
            page = productRepository.findByNameContainingIgnoreCase(name, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }

        return new PagedResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    public ProductResponse updateProduct(String id, UpdateProductRequest request) {
        Product product = findById(id);

        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.price() != null) product.setPrice(request.price());
        if (request.categoryId() != null) product.setCategoryId(request.categoryId());
        if (request.stockQuantity() != null) product.setStockQuantity(request.stockQuantity());
        if (request.imageUrl() != null) product.setImageUrl(request.imageUrl());
        if (request.attributes() != null) product.setAttributes(request.attributes());
        product.setUpdatedAt(LocalDateTime.now());

        return toResponse(productRepository.save(product));
    }

    public void deleteProduct(String id) {
        Product product = findById(id);
        productRepository.delete(product);
    }

    public ProductResponse updateStock(String id, StockUpdateRequest request) {
        Product product = findById(id);
        int newQuantity = product.getStockQuantity() + request.delta();
        if (newQuantity < 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock. Current: " + product.getStockQuantity() + ", delta: " + request.delta());
        }
        product.setStockQuantity(newQuantity);
        product.setUpdatedAt(LocalDateTime.now());
        return toResponse(productRepository.save(product));
    }

    private Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getCategoryId(),
                p.getStockQuantity(),
                p.getImageUrl(),
                p.getAttributes(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
