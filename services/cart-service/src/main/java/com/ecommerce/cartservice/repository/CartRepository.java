package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.dto.Cart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class CartRepository {

    private static final Logger log = LoggerFactory.getLogger(CartRepository.class);
    private static final String KEY_PREFIX = "cart:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public CartRepository(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Cart> findByUserId(String userId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Cart.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cart for userId={}", userId);
            return Optional.empty();
        }
    }

    public void save(Cart cart, long ttlDays) {
        try {
            String json = objectMapper.writeValueAsString(cart);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + cart.getUserId(),
                    json,
                    Duration.ofDays(ttlDays)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cart for userId={}", cart.getUserId());
            throw new IllegalStateException("Failed to save cart", e);
        }
    }

    public void delete(String userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
