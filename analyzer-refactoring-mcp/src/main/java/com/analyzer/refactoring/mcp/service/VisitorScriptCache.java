package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.analyzer.refactoring.mcp.model.ScriptMetadata;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
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
    private final GroovyScriptRepository repository;

    public VisitorScriptCache(
            @Value("${groovy.cache.enabled:true}") final boolean enabled,
            @Value("${groovy.cache.max-size:100}") final int maxSize,
            @Value("${groovy.cache.expire-after-write-minutes:60}") final int expireMinutes,
            @Value("${groovy.cache.record-stats:true}") final boolean recordStats,
            final GroovyScriptRepository repository) {

        this.enabled = enabled;
        this.repository = repository;

        if (enabled) {
            final Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .expireAfterWrite(Duration.ofMinutes(expireMinutes));

            if (recordStats) {
                builder.recordStats();
            }

            cache = builder.build();

            logger.info("Visitor script cache initialized: maxSize={}, expireAfterWrite={}min, recordStats={}",
                    maxSize, expireMinutes, recordStats);
        } else {
            cache = null;
            logger.info("Visitor script cache disabled");
        }
    }

    /**
     * Warm cache from disk on startup.
     */
    @PostConstruct
    public void warmCache() {
        if (!enabled || !repository.isEnabled()) {
            return;
        }

        try {
            final List<String> scriptHashes = repository.listScriptHashes();
            int loaded = 0;

            for (final String scriptHash : scriptHashes) {
                final Optional<OpenRewriteVisitorScript> scriptOpt = repository.loadScript(scriptHash);
                final Optional<ScriptMetadata> metadataOpt = repository.loadMetadata(scriptHash);

                if (scriptOpt.isPresent() && metadataOpt.isPresent()) {
                    final ScriptMetadata metadata = metadataOpt.get();
                    cache.put(scriptHash, scriptOpt.get());
                    loaded++;
                }
            }

            if (loaded > 0) {
                logger.info("Warmed cache with {} scripts from disk", loaded);
            }

        } catch (final Exception e) {
            logger.error("Failed to warm cache from disk", e);
        }
    }

    /**
     * Get a cached script if available.
     * First checks in-memory cache, then falls back to disk storage.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern description
     * @param nodeType           the node type
     * @param filePaths          optional list of file paths
     * @return the cached script, or empty if not found
     */
    public Optional<OpenRewriteVisitorScript> get(
            final String projectPath,
            final String patternDescription,
            final String nodeType,
            final List<String> filePaths) {

        if (!enabled) {
            return Optional.empty();
        }

        final String key = generateCacheKey(projectPath, patternDescription, nodeType, filePaths);

        // Check in-memory cache first
        final OpenRewriteVisitorScript script = cache.getIfPresent(key);

        if (script != null) {
            logger.debug("Memory cache hit for pattern: {}, nodeType: {}", patternDescription, nodeType);
            return Optional.of(script);
        }

        // Check disk storage if memory cache miss
        if (repository.isEnabled()) {
            final Optional<OpenRewriteVisitorScript> diskScript = repository.loadScript(key);
            if (diskScript.isPresent()) {
                logger.debug("Disk cache hit for pattern: {}, nodeType: {}", patternDescription, nodeType);
                // Load into memory cache for faster access next time
                cache.put(key, diskScript.get());

                // Update metadata usage
                repository.loadMetadata(key).ifPresent(metadata -> {
                    metadata.recordSuccess();
                    repository.updateMetadata(key, metadata);
                });

                return diskScript;
            }
        }

        logger.debug("Cache miss (memory + disk) for pattern: {}, nodeType: {}", patternDescription, nodeType);
        return Optional.empty();
    }

    /**
     * Put a script into the cache and persist to disk.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern description
     * @param nodeType           the node type
     * @param filePaths          optional list of file paths
     * @param script             the compiled script to cache
     */
    public void put(
            final String projectPath,
            final String patternDescription,
            final String nodeType,
            final List<String> filePaths,
            final OpenRewriteVisitorScript script) {

        if (!enabled) {
            return;
        }

        final String key = generateCacheKey(projectPath, patternDescription, nodeType, filePaths);

        // Store in memory cache
        cache.put(key, script);

        // Persist to disk if repository is enabled
        if (repository.isEnabled()) {
            final ScriptMetadata metadata = ScriptMetadata.create(
                    key,
                    patternDescription,
                    nodeType,
                    projectPath,
                    filePaths,
                    script.getGenerationAttempts(),
                    true // validated since it was successfully generated
            );

            repository.saveScript(key, script.getSourceCode(), metadata);
        }

        logger.info("Cached script for pattern: {}, nodeType: {}, attempts: {}",
                patternDescription, nodeType, script.getGenerationAttempts());
    }

    /**
     * Invalidate (remove) a cached script.
     * This is useful when a script execution fails and needs to be regenerated.
     *
     * @param projectPath        the project path
     * @param patternDescription the pattern description
     * @param nodeType           the node type
     * @param filePaths          optional list of file paths
     */
    public void invalidate(
            final String projectPath,
            final String patternDescription,
            final String nodeType,
            final List<String> filePaths) {

        if (!enabled) {
            return;
        }

        final String key = generateCacheKey(projectPath, patternDescription, nodeType, filePaths);

        // Remove from memory cache
        cache.invalidate(key);

        // Note: We don't delete from disk storage, as the script might be useful
        // for analysis or debugging. Disk storage has its own cleanup mechanism.

        logger.info("Invalidated cache for pattern: {}, nodeType: {}", patternDescription, nodeType);
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
            final String projectPath,
            final String patternDescription,
            final String nodeType,
            final List<String> filePaths) {

        final StringBuilder sb = new StringBuilder();
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
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (final NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available
            logger.warn("SHA-256 not available, using simple hash", e);
            return String.valueOf(sb.toString().hashCode());
        }
    }

    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder();
        for (final byte b : bytes) {
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
