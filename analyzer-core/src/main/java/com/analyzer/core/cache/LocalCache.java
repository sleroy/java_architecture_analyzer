package com.analyzer.core.cache;

import com.github.javaparser.ast.CompilationUnit;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-item cache for optimizing repeated access to the same resources during
 * analysis.
 * <p>
 * This cache lives for the duration of processing a single ProjectFile or
 * JavaClassNode,
 * caching expensive operations like:
 * - JavaParser CompilationUnit parsing
 * - ASM ClassReader and bytecode loading
 * - Source file content reading
 * - Resource location resolution
 * <p>
 * The cache is reset between items to prevent memory bloat.
 */
public class LocalCache {

    private static final Logger logger = LoggerFactory.getLogger(LocalCache.class);

    private final boolean enabled;

    // Core cached resources (type-safe fields for common types)
    private Object resourceLocation; // Using Object to avoid circular dependency
    private CompilationUnit javaParserCompilationUnit;
    private byte[] classBytes;
    private ClassReader asmClassReader;
    private String sourceContent;

    // Extension point for custom caching needs
    private final Map<String, Object> customCache;

    /**
     * Creates a new LocalCache.
     *
     * @param enabled whether caching is enabled
     */
    public LocalCache(boolean enabled) {
        this.enabled = enabled;
        this.customCache = new HashMap<>();
        logger.debug("LocalCache created with caching {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Resets all cached values. Should be called before processing each new item.
     */
    public void reset() {
        resourceLocation = null;
        javaParserCompilationUnit = null;
        classBytes = null;
        asmClassReader = null;
        sourceContent = null;
        customCache.clear();
        logger.trace("LocalCache reset");
    }

    /**
     * Gets or resolves a resource location. If caching is enabled and a value
     * exists,
     * returns the cached value. Otherwise, uses the supplier to compute and cache
     * it.
     *
     * @param resolver supplier to resolve the location if not cached
     * @return the resource location
     */
    public Object getOrResolveLocation(Supplier<Object> resolver) {
        if (!enabled || resourceLocation == null) {
            resourceLocation = resolver.get();
            logger.trace("ResourceLocation cached");
        } else {
            logger.trace("ResourceLocation retrieved from cache");
        }
        return resourceLocation;
    }

    /**
     * Gets or parses a CompilationUnit. If caching is enabled and a value exists,
     * returns the cached value. Otherwise, uses the supplier to compute and cache
     * it.
     *
     * @param parser supplier to parse the compilation unit if not cached
     * @return the compilation unit, or null if parsing failed
     */
    public CompilationUnit getOrParseCompilationUnit(Supplier<CompilationUnit> parser) {
        if (!enabled || javaParserCompilationUnit == null) {
            javaParserCompilationUnit = parser.get();
            logger.trace("CompilationUnit parsed and cached");
        } else {
            logger.trace("CompilationUnit retrieved from cache");
        }
        return javaParserCompilationUnit;
    }

    /**
     * Gets or loads class bytes. If caching is enabled and a value exists,
     * returns the cached value. Otherwise, uses the supplier to compute and cache
     * it.
     *
     * @param loader supplier to load the bytes if not cached
     * @return the class bytes
     */
    public byte[] getOrLoadClassBytes(Supplier<byte[]> loader) {
        if (!enabled || classBytes == null) {
            classBytes = loader.get();
            logger.trace("Class bytes loaded and cached ({} bytes)",
                    classBytes != null ? classBytes.length : 0);
        } else {
            logger.trace("Class bytes retrieved from cache ({} bytes)", classBytes.length);
        }
        return classBytes;
    }

    /**
     * Gets or creates an ASM ClassReader. If caching is enabled and a value exists,
     * returns the cached value. Otherwise, uses the supplier to compute and cache
     * it.
     *
     * @param creator supplier to create the ClassReader if not cached
     * @return the ClassReader
     */
    public ClassReader getOrCreateClassReader(Supplier<ClassReader> creator) {
        if (!enabled || asmClassReader == null) {
            asmClassReader = creator.get();
            logger.trace("ClassReader created and cached");
        } else {
            logger.trace("ClassReader retrieved from cache");
        }
        return asmClassReader;
    }

    /**
     * Gets or loads source content. If caching is enabled and a value exists,
     * returns the cached value. Otherwise, uses the supplier to compute and cache
     * it.
     *
     * @param loader supplier to load the content if not cached
     * @return the source content
     */
    public String getOrLoadSourceContent(Supplier<String> loader) {
        if (!enabled || sourceContent == null) {
            sourceContent = loader.get();
            logger.trace("Source content loaded and cached ({} chars)",
                    sourceContent != null ? sourceContent.length() : 0);
        } else {
            logger.trace("Source content retrieved from cache ({} chars)", sourceContent.length());
        }
        return sourceContent;
    }

    /**
     * Gets or computes a custom cached value identified by a key.
     * This provides an extension point for inspectors to cache custom data.
     *
     * @param key      the cache key
     * @param supplier supplier to compute the value if not cached
     * @param <T>      the type of the cached value
     * @return the cached or computed value
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        if (!enabled) {
            return supplier.get();
        }

        Object value = customCache.get(key);
        if (value == null) {
            value = supplier.get();
            customCache.put(key, value);
            logger.trace("Custom value cached with key: {}", key);
        } else {
            logger.trace("Custom value retrieved from cache with key: {}", key);
        }
        return (T) value;
    }

    /**
     * Checks if caching is enabled.
     *
     * @return true if caching is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
