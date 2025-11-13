package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Cache service for compiled OpenRewrite visitor scripts.
 * Uses Caffeine for high-performance in-memory caching with automatic eviction.
 */
@Service
public class VisitorScriptCache {

    private static final Logger logger = LoggerFactory.getLogger(VisitorScriptCache.class);

    private final Cache<String, OpenRewriteVisitorScript> cache;
    private final boolean enabled;

    public VisitorScriptCache(
            @Value("${groovy.cache.enabled:true}") boolean enabled,
            @Value("${groovy.cache.max-size:100}") int maxSize,
            @Value("${groovy.cache.expire-after-write-minutes:60}") int expireMinutes,
            @Value("${groovy.cache.record-stats:true}") boolean recordStats) {

        this.enabled = enabled;

        if (enabled) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .expireAfterWrite(Duration.ofMinutes(expireMinutes));

            if (recordStats) {
                builder.recordStats();
            }

            this.cache = builder.build();

            logger.info("Visitor script cache initialized: maxSize={}, expireAfterWrite={}min, recordStats={}",
                    maxSize, expireMinutes, recordStats);
        } else {
            this.cache = null;
            logger.info("Visitor script cache disabled");
        }
    }

    /**
     * Get a cached script if available.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern description
     * @param nodeType           the node type
     * @param filePaths          optional list of file paths
     * @return the cached script, or empty if not found
     */
    public Optional<OpenRewriteVisitorScript> get(
            String projectPath,
            String patternDescription,
            String nodeType,
            List<String> filePaths) {

        if (!enabled) {
            return Optional.empty();
        }

        String key = generateCacheKey(projectPath, patternDescription, nodeType, filePaths);
        OpenRewriteVisitorScript script = cache.getIfPresent(key);

        if (script != null) {
            logger.debug("Cache hit for pattern: {}, nodeType: {}", patternDescription, nodeType);
        } else {
            logger.debug("Cache miss for pattern: {}, nodeType: {}", patternDescription, nodeType);
        }

        return Optional.ofNullable(script);
    }

    /**
     * Put a script into the cache.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern description
     * @param nodeType           the node type
     * @param filePaths          optional list of file paths
     * @param script             the compiled script to cache
     */
    public void put(
            String projectPath,
            String patternDescription,
            String nodeType,
            List<String> filePaths,
            OpenRewriteVisitorScript script) {

        if (!enabled) {
            return;
        }

        String key = generateCacheKey(projectPath, patternDescription, nodeType, filePaths);
        cache.put(key, script);

        logger.info("Cached script for pattern: {}, nodeType: {}, attempts: {}",
                patternDescription, nodeType, script.getGenerationAttempts());
    }

    /**
     * Generate a cache key from the parameters.
     * Uses SHA-256 hash of the concatenated parameters for consistent key
     * generation.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern description
     * @param nodeType           the node type
     * @param filePaths          optional list of file paths
     * @return the cache key
     */
    private String generateCacheKey(
            String projectPath,
            String patternDescription,
            String nodeType,
            List<String> filePaths) {

        StringBuilder sb = new StringBuilder();
        sb.append(projectPath != null ? projectPath : "");
        sb.append("|");
        sb.append(patternDescription);
        sb.append("|");
        sb.append(nodeType);
        sb.append("|");
        if (filePaths != null && !filePaths.isEmpty()) {
            sb.append(String.join(",", filePaths));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available
            logger.warn("SHA-256 not available, using simple hash", e);
            return String.valueOf(sb.toString().hashCode());
        }
    }

    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Clear all cached scripts.
     */
    public void clear() {
        if (enabled && cache != null) {
            cache.invalidateAll();
            logger.info("Cache cleared");
        }
    }

    /**
     * Get cache statistics.
     *
     * @return cache statistics, or empty if stats not enabled
     */
    public Optional<CacheStats> getStats() {
        if (enabled && cache != null) {
            return Optional.of(cache.stats());
        }
        return Optional.empty();
    }

    /**
     * Get current cache size.
     *
     * @return the number of entries in the cache
     */
    public long size() {
        if (enabled && cache != null) {
            return cache.estimatedSize();
        }
        return 0;
    }

    /**
     * Check if cache is enabled.
     *
     * @return true if cache is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
