package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteRecipeScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.script.CompiledScript;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for RecipeScriptCache.
 * Tests the caching behavior for OpenRewrite recipe scripts.
 */
class RecipeScriptCacheTest {

    private RecipeScriptCache cache;
    private CompiledScript mockScript;

    @BeforeEach
    void setUp() {
        cache = new RecipeScriptCache();
        mockScript = mock(CompiledScript.class);
    }

    @Test
    void testCacheMiss() {
        Optional<OpenRewriteRecipeScript> result = cache.get(
                "/project",
                "singleton pattern",
                "ClassDeclaration",
                "convert to Spring",
                null);

        assertFalse(result.isPresent(), "Cache should be empty initially");
    }

    @Test
    void testCacheHit() {
        // Create and cache a recipe script
        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("singleton pattern")
                .transformation("convert to Spring")
                .nodeType("ClassDeclaration")
                .projectPath("/project")
                .compiledScript(mockScript)
                .sourceCode("test recipe script")
                .generationAttempts(1)
                .build();

        cache.put("/project", "singleton pattern", "ClassDeclaration", "convert to Spring", null, script);

        // Retrieve from cache
        Optional<OpenRewriteRecipeScript> result = cache.get(
                "/project",
                "singleton pattern",
                "ClassDeclaration",
                "convert to Spring",
                null);

        assertTrue(result.isPresent(), "Cache should contain the recipe");
        assertEquals("singleton pattern", result.get().getPatternDescription());
        assertEquals("convert to Spring", result.get().getTransformation());
        assertEquals("ClassDeclaration", result.get().getNodeType());
    }

    @Test
    void testCacheKeyIncludesAllParameters() {
        OpenRewriteRecipeScript script1 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern1")
                .transformation("transformation1")
                .nodeType("ClassDeclaration")
                .projectPath("/project1")
                .compiledScript(mockScript)
                .sourceCode("script1")
                .build();

        OpenRewriteRecipeScript script2 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern2")
                .transformation("transformation2")
                .nodeType("MethodDeclaration")
                .projectPath("/project2")
                .compiledScript(mockScript)
                .sourceCode("script2")
                .build();

        // Cache both scripts
        cache.put("/project1", "pattern1", "ClassDeclaration", "transformation1", null, script1);
        cache.put("/project2", "pattern2", "MethodDeclaration", "transformation2", null, script2);

        // Verify both can be retrieved independently
        Optional<OpenRewriteRecipeScript> result1 = cache.get(
                "/project1", "pattern1", "ClassDeclaration", "transformation1", null);
        Optional<OpenRewriteRecipeScript> result2 = cache.get(
                "/project2", "pattern2", "MethodDeclaration", "transformation2", null);

        assertTrue(result1.isPresent(), "First recipe should be cached");
        assertTrue(result2.isPresent(), "Second recipe should be cached");
        assertEquals("script1", result1.get().getSourceCode());
        assertEquals("script2", result2.get().getSourceCode());
    }

    @Test
    void testCacheKeyIncludesFilePaths() {
        List<String> filePaths1 = Arrays.asList("File1.java", "File2.java");
        List<String> filePaths2 = Arrays.asList("File3.java");

        OpenRewriteRecipeScript script1 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script1")
                .build();

        OpenRewriteRecipeScript script2 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script2")
                .build();

        // Cache with different file paths
        cache.put("/project", "pattern", "ClassDeclaration", "transform", filePaths1, script1);
        cache.put("/project", "pattern", "ClassDeclaration", "transform", filePaths2, script2);

        // Verify different cache keys
        Optional<OpenRewriteRecipeScript> result1 = cache.get(
                "/project", "pattern", "ClassDeclaration", "transform", filePaths1);
        Optional<OpenRewriteRecipeScript> result2 = cache.get(
                "/project", "pattern", "ClassDeclaration", "transform", filePaths2);

        assertTrue(result1.isPresent(), "First recipe with filePaths1 should be cached");
        assertTrue(result2.isPresent(), "Second recipe with filePaths2 should be cached");
        assertEquals("script1", result1.get().getSourceCode());
        assertEquals("script2", result2.get().getSourceCode());
    }

    @Test
    void testCacheKeyWithTransformation() {
        // Same pattern but different transformations should be different cache keys
        OpenRewriteRecipeScript script1 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transformation1")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script1")
                .build();

        OpenRewriteRecipeScript script2 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transformation2")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script2")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transformation1", null, script1);
        cache.put("/project", "pattern", "ClassDeclaration", "transformation2", null, script2);

        Optional<OpenRewriteRecipeScript> result1 = cache.get(
                "/project", "pattern", "ClassDeclaration", "transformation1", null);
        Optional<OpenRewriteRecipeScript> result2 = cache.get(
                "/project", "pattern", "ClassDeclaration", "transformation2", null);

        assertTrue(result1.isPresent(), "First transformation should be cached");
        assertTrue(result2.isPresent(), "Second transformation should be cached");
        assertEquals("script1", result1.get().getSourceCode());
        assertEquals("script2", result2.get().getSourceCode());
    }

    @Test
    void testCacheClear() {
        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script);

        // Verify cached
        assertTrue(cache.get("/project", "pattern", "ClassDeclaration", "transform", null).isPresent(),
                "Recipe should be cached before clear");

        // Clear cache
        cache.clear();

        // Verify cleared
        assertFalse(cache.get("/project", "pattern", "ClassDeclaration", "transform", null).isPresent(),
                "Recipe should not be cached after clear");
    }

    @Test
    void testCacheInvalidate() {
        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script);

        // Verify cached
        assertTrue(cache.get("/project", "pattern", "ClassDeclaration", "transform", null).isPresent(),
                "Recipe should be cached before invalidation");

        // Invalidate specific entry
        cache.invalidate("/project", "pattern", "ClassDeclaration", "transform", null);

        // Verify invalidated
        assertFalse(cache.get("/project", "pattern", "ClassDeclaration", "transform", null).isPresent(),
                "Recipe should not be cached after invalidation");
    }

    @Test
    void testCacheSize() {
        assertEquals(0, cache.size(), "Cache should be empty initially");

        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script);

        assertEquals(1, cache.size(), "Cache should contain one entry");
    }

    @Test
    void testCacheStats() {
        RecipeScriptCache.CacheStats stats = cache.getStats();
        assertNotNull(stats, "Stats should not be null");
        assertEquals(0, stats.getTotalEntries(), "Should have no entries initially");

        // Perform cache miss
        cache.get("/project", "pattern", "ClassDeclaration", "transform", null);

        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script);

        // Perform cache hit
        cache.get("/project", "pattern", "ClassDeclaration", "transform", null);

        stats = cache.getStats();
        assertEquals(1, stats.getTotalEntries(), "Should have one entry");
        assertEquals(0, stats.getExpiredEntries(), "Should have no expired entries yet");
    }

    @Test
    void testCacheKeyConsistency() {
        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        // Put with specific parameters
        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script);

        // Get with same parameters multiple times
        for (int i = 0; i < 5; i++) {
            Optional<OpenRewriteRecipeScript> result = cache.get(
                    "/project", "pattern", "ClassDeclaration", "transform", null);
            assertTrue(result.isPresent(), "Cache hit should succeed on attempt " + i);
            assertEquals("script", result.get().getSourceCode());
        }
    }

    @Test
    void testCacheWithComplexTransformation() {
        // Test with a long, complex transformation description
        String complexTransformation = "Rename all methods starting with 'old' to start with 'new' " +
                "and update all method invocations accordingly, preserving parameter types and return values";

        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("method pattern")
                .transformation(complexTransformation)
                .nodeType("MethodDeclaration")
                .compiledScript(mockScript)
                .sourceCode("complex script")
                .build();

        cache.put("/project", "method pattern", "MethodDeclaration", complexTransformation, null, script);

        Optional<OpenRewriteRecipeScript> result = cache.get(
                "/project", "method pattern", "MethodDeclaration", complexTransformation, null);

        assertTrue(result.isPresent(), "Recipe with complex transformation should be cached");
        assertEquals(complexTransformation, result.get().getTransformation());
    }

    @Test
    void testNullHandling() {
        // Test graceful handling of null parameters
        OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        // Put with null projectPath
        cache.put(null, "pattern", "ClassDeclaration", "transform", null, script);

        // Get with null projectPath
        Optional<OpenRewriteRecipeScript> result = cache.get(
                null, "pattern", "ClassDeclaration", "transform", null);

        assertTrue(result.isPresent(), "Should handle null projectPath gracefully");
    }

    @Test
    void testMultipleEntriesCache() {
        // Add multiple different recipes
        for (int i = 0; i < 5; i++) {
            OpenRewriteRecipeScript script = OpenRewriteRecipeScript.builder()
                    .patternDescription("pattern" + i)
                    .transformation("transform" + i)
                    .nodeType("ClassDeclaration")
                    .compiledScript(mockScript)
                    .sourceCode("script" + i)
                    .build();

            cache.put("/project", "pattern" + i, "ClassDeclaration", "transform" + i, null, script);
        }

        assertEquals(5, cache.size(), "Cache should contain 5 entries");

        // Verify all can be retrieved
        for (int i = 0; i < 5; i++) {
            Optional<OpenRewriteRecipeScript> result = cache.get(
                    "/project", "pattern" + i, "ClassDeclaration", "transform" + i, null);
            assertTrue(result.isPresent(), "Entry " + i + " should be cached");
            assertEquals("script" + i, result.get().getSourceCode());
        }
    }

    @Test
    void testCacheReplacementSameKey() {
        // Put first recipe
        OpenRewriteRecipeScript script1 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script1")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script1);

        // Put second recipe with same key (should replace)
        OpenRewriteRecipeScript script2 = OpenRewriteRecipeScript.builder()
                .patternDescription("pattern")
                .transformation("transform")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script2")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", "transform", null, script2);

        // Should still have only 1 entry
        assertEquals(1, cache.size(), "Cache should contain only one entry");

        // Should retrieve the second script
        Optional<OpenRewriteRecipeScript> result = cache.get(
                "/project", "pattern", "ClassDeclaration", "transform", null);

        assertTrue(result.isPresent(), "Recipe should be cached");
        assertEquals("script2", result.get().getSourceCode(), "Should retrieve the most recent script");
    }
}
