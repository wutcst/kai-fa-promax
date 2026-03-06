package com.guandan.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 缓存服务（当前内存实现，后续切 Redis）。
 */
@Service
public class TokenCache {
    private final Map<Long, String> cache = new ConcurrentHashMap<>();

    public void cacheToken(Long userId, String token) {
        cache.put(userId, token);
    }

    public boolean validateToken(Long userId, String token) {
        String cached = cache.get(userId);
        return cached != null && cached.equals(token);
    }

    public void removeToken(Long userId) {
        cache.remove(userId);
    }
}
