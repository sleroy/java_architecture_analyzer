package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.script.CompiledScript;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for VisitorScriptCache.
 */
class VisitorScriptCacheTest {

    private VisitorScriptCache cache;
    private CompiledScript mockScript;

    @BeforeEach
    void setUp() {
        // Create cache with small size for testing
        cache = new VisitorScriptCache(true, 10, 1, true);
        mockScript = mock(CompiledScript.class);
    }

    @Test
    void testCacheEnabled() {
        assertTrue(cache.isEnabled());
    }

    @Test
    void testCacheMiss() {
        Optional<OpenRewriteVisitorScript> result = cache.get(
                "/project",
                "singleton pattern",
                "ClassDeclaration",
                null);

        assertFalse(result.isPresent());
    }

    @Test
    void testCacheHit() {
        // Create and cache a script
        OpenRewriteVisitorScript script = OpenRewriteVisitorScript.builder()
                .patternDescription("singleton pattern")
                .nodeType("ClassDeclaration")
                .projectPath("/project")
                .compiledScript(mockScript)
                .sourceCode("test script")
                .generationAttempts(1)
                .build();

        cache.put("/project", "singleton pattern", "ClassDeclaration", null, script);

        // Retrieve from cache
        Optional<OpenRewriteVisitorScript> result = cache.get(
                "/project",
                "singleton pattern",
                "ClassDeclaration",
                null);

        assertTrue(result.isPresent());
        assertEquals("singleton pattern", result.get().getPatternDescription());
        assertEquals("ClassDeclaration", result.get().getNodeType());
    }

    @Test
    void testCacheKeyIncludesAllParameters() {
        OpenRewriteVisitorScript script1 = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern1")
                .nodeType("ClassDeclaration")
                .projectPath("/project1")
                .compiledScript(mockScript)
                .sourceCode("script1")
                .build();

        OpenRewriteVisitorScript script2 = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern2")
                .nodeType("MethodDeclaration")
                .projectPath("/project2")
                .compiledScript(mockScript)
                .sourceCode("script2")
                .build();

        // Cache both scripts
        cache.put("/project1", "pattern1", "ClassDeclaration", null, script1);
        cache.put("/project2", "pattern2", "MethodDeclaration", null, script2);

        // Verify both can be retrieved independently
        Optional<OpenRewriteVisitorScript> result1 = cache.get(
                "/project1", "pattern1", "ClassDeclaration", null);
        Optional<OpenRewriteVisitorScript> result2 = cache.get(
                "/project2", "pattern2", "MethodDeclaration", null);

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals("script1", result1.get().getSourceCode());
        assertEquals("script2", result2.get().getSourceCode());
    }

    @Test
    void testCacheKeyIncludesFilePaths() {
        List<String> filePaths1 = Arrays.asList("File1.java", "File2.java");
        List<String> filePaths2 = Arrays.asList("File3.java");

        OpenRewriteVisitorScript script1 = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script1")
                .build();

        OpenRewriteVisitorScript script2 = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script2")
                .build();

        // Cache with different file paths
        cache.put("/project", "pattern", "ClassDeclaration", filePaths1, script1);
        cache.put("/project", "pattern", "ClassDeclaration", filePaths2, script2);

        // Verify different cache keys
        Optional<OpenRewriteVisitorScript> result1 = cache.get(
                "/project", "pattern", "ClassDeclaration", filePaths1);
        Optional<OpenRewriteVisitorScript> result2 = cache.get(
                "/project", "pattern", "ClassDeclaration", filePaths2);

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals("script1", result1.get().getSourceCode());
        assertEquals("script2", result2.get().getSourceCode());
    }

    @Test
    void testCacheClear() {
        OpenRewriteVisitorScript script = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", null, script);

        // Verify cached
        assertTrue(cache.get("/project", "pattern", "ClassDeclaration", null).isPresent());

        // Clear cache
        cache.clear();

        // Verify cleared
        assertFalse(cache.get("/project", "pattern", "ClassDeclaration", null).isPresent());
    }

    @Test
    void testCacheSize() {
        assertEquals(0, cache.size());

        OpenRewriteVisitorScript script = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", null, script);

        assertEquals(1, cache.size());
    }

    @Test
    void testCacheStats() {
        Optional<CacheStats> stats = cache.getStats();
        assertTrue(stats.isPresent());

        // Perform some cache operations
        cache.get("/project", "pattern", "ClassDeclaration", null); // Miss

        OpenRewriteVisitorScript script = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        cache.put("/project", "pattern", "ClassDeclaration", null, script);
        cache.get("/project", "pattern", "ClassDeclaration", null); // Hit

        // Verify stats recorded
        stats = cache.getStats();
        assertTrue(stats.isPresent());
        assertTrue(stats.get().missCount() > 0);
        assertTrue(stats.get().hitCount() > 0);
    }

    @Test
    void testDisabledCache() {
        VisitorScriptCache disabledCache = new VisitorScriptCache(false, 10, 1, true);

        assertFalse(disabledCache.isEnabled());
        assertEquals(0, disabledCache.size());

        OpenRewriteVisitorScript script = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        // Put should be no-op
        disabledCache.put("/project", "pattern", "ClassDeclaration", null, script);

        // Get should return empty
        Optional<OpenRewriteVisitorScript> result = disabledCache.get(
                "/project", "pattern", "ClassDeclaration", null);

        assertFalse(result.isPresent());
        assertEquals(0, disabledCache.size());
    }

    @Test
    void testCacheKeyConsistency() {
        OpenRewriteVisitorScript script = OpenRewriteVisitorScript.builder()
                .patternDescription("pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(mockScript)
                .sourceCode("script")
                .build();

        // Put with specific parameters
        cache.put("/project", "pattern", "ClassDeclaration", null, script);

        // Get with same parameters multiple times
        for (int i = 0; i < 5; i++) {
            Optional<OpenRewriteVisitorScript> result = cache.get(
                    "/project", "pattern", "ClassDeclaration", null);
            assertTrue(result.isPresent(), "Cache hit should succeed on attempt " + i);
        }
    }
}
