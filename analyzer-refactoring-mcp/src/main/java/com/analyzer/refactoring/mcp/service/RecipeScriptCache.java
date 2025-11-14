package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteRecipeScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for compiled OpenRewrite recipe scripts.
 * Similar to VisitorScriptCache but caches recipes for transformations.
 * 
 * Caching strategies:
 * - In-memory cache with 60-minute expiration
 * - Key based on: projectPath + patternDescription + nodeType + transformation
 * + filePaths
 * - LRU eviction when cache size exceeds limit
 * - Thread-safe using ConcurrentHashMap
 */
@Service
public class RecipeScriptCache {

    private static final Logger logger = LoggerFactory.getLogger(RecipeScriptCache.class);

    private static final int MAX_CACHE_SIZE = 100;
    private static final long CACHE_EXPIRATION_MS = 60 * 60 * 1000; // 60 minutes

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Get cached recipe script if available and not expired.
     */
    public Optional<OpenRewriteRecipeScript> get(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths) {

        String key = buildKey(projectPath, patternDescription, nodeType, transformation, filePaths);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            logger.debug("Cache miss for key: {}", key);
            return Optional.empty();
        }

        // Check if expired
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRATION_MS) {
            logger.debug("Cache entry expired for key: {}", key);
            cache.remove(key);
            return Optional.empty();
        }

        logger.debug("Cache hit for key: {}", key);
        entry.lastAccessed = System.currentTimeMillis();
        return Optional.of(entry.script);
    }

    /**
     * Put recipe script into cache.
     */
    public void put(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths,
            OpenRewriteRecipeScript script) {

        String key = buildKey(projectPath, patternDescription, nodeType, transformation, filePaths);

        // Evict oldest entries if cache is full
        if (cache.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }

        CacheEntry entry = new CacheEntry(script, System.currentTimeMillis());
        cache.put(key, entry);

        logger.debug("Cached recipe script for key: {} (cache size: {})", key, cache.size());
    }

    /**
     * Invalidate a specific cache entry.
     */
    public void invalidate(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths) {

        String key = buildKey(projectPath, patternDescription, nodeType, transformation, filePaths);
        cache.remove(key);
        logger.debug("Invalidated cache entry for key: {}", key);
    }

    /**
     * Clear all cache entries.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        logger.info("Cleared recipe script cache ({} entries)", size);
    }

    /**
     * Get current cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        int totalEntries = cache.size();
        int expiredEntries = 0;
        long currentTime = System.currentTimeMillis();

        for (CacheEntry entry : cache.values()) {
            if (currentTime - entry.timestamp > CACHE_EXPIRATION_MS) {
                expiredEntries++;
            }
        }

        return new CacheStats(totalEntries, expiredEntries, MAX_CACHE_SIZE);
    }

    /**
     * Build cache key from parameters.
     */
    private String buildKey(
            String projectPath,
            String patternDescription,
            String nodeType,
            String transformation,
            List<String> filePaths) {

        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(projectPath != null ? projectPath : "");
        keyBuilder.append("|");
        keyBuilder.append(patternDescription);
        keyBuilder.append("|");
        keyBuilder.append(nodeType);
        keyBuilder.append("|");
        keyBuilder.append(transformation);
        keyBuilder.append("|");

        if (filePaths != null && !filePaths.isEmpty()) {
            keyBuilder.append(String.join(",", filePaths));
        }

        return keyBuilder.toString();
    }

    /**
     * Evict the oldest (least recently accessed) entry.
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().lastAccessed < oldestTime) {
                oldestTime = entry.getValue().lastAccessed;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
            logger.debug("Evicted oldest cache entry: {}", oldestKey);
        }
    }

    /**
     * Cache entry wrapper.
     */
    private static class CacheEntry {
        final OpenRewriteRecipeScript script;
        final long timestamp;
        long lastAccessed;

        CacheEntry(OpenRewriteRecipeScript script, long timestamp) {
            this.script = script;
            this.timestamp = timestamp;
            this.lastAccessed = timestamp;
        }
    }

    /**
     * Cache statistics.
     */
    public static class CacheStats {
        private final int totalEntries;
        private final int expiredEntries;
        private final int maxSize;

        public CacheStats(int totalEntries, int expiredEntries, int maxSize) {
            this.totalEntries = totalEntries;
            this.expiredEntries = expiredEntries;
            this.maxSize = maxSize;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public int getExpiredEntries() {
            return expiredEntries;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public int getActiveEntries() {
            return totalEntries - expiredEntries;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{total=%d, active=%d, expired=%d, max=%d}",
                    totalEntries, getActiveEntries(), expiredEntries, maxSize);
        }
    }
}
