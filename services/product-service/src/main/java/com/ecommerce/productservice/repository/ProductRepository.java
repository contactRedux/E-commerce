package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.document.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByCategoryId(String categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByCategoryIdAndNameContainingIgnoreCase(String categoryId, String name, Pageable pageable);

    Page<Product> findByStockQuantityGreaterThan(int quantity, Pageable pageable);

    boolean existsByCategoryId(String categoryId);
}
