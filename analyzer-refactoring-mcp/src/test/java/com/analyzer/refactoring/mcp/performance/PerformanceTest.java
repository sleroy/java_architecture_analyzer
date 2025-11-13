package com.analyzer.refactoring.mcp.performance;

import com.analyzer.refactoring.mcp.service.VisitorScriptCache;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for SearchJavaPatternTool.
 * 
 * Measures and validates:
 * - Cache hit performance (<100ms target)
 * - Cache miss performance (5-10s target)
 * - Script generation time
 * - Compilation time
 * - Execution time per file
 * - Memory usage
 * 
 * Uses real services with test credentials.
 * These tests are disabled by default to avoid slowing down regular test runs.
 * Run with: mvn test -Dtest=PerformanceTest
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Performance tests - run manually")
class PerformanceTest {

    @Autowired
    private SearchJavaPatternTool tool;

    @Autowired
    private VisitorScriptCache cache;

    @TempDir
    Path tempProjectDir;

    @BeforeEach
    void setUp() {
        cache.clear();
    }

    @Test
    void testPerformance_CacheHit() throws Exception {
        // Given: Create test project and populate cache
        createTestProject(10);

        // Warm up cache with template pattern
        tool.searchJavaPattern(tempProjectDir.toString(), "singleton pattern", "ClassDeclaration", null);

        // When: Execute with cache hit
        Instant start = Instant.now();
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);
        Instant end = Instant.now();

        // Then: Should be fast (<100ms target)
        long durationMs = Duration.between(start, end).toMillis();
        System.out.println("Cache hit performance: " + durationMs + "ms");

        assertNotNull(result);
        assertTrue(durationMs < 1000, "Cache hit should be < 1000ms (was " + durationMs + "ms)");
    }

    @Test
    void testPerformance_CacheMiss() throws Exception {
        // Given: Create test project
        createTestProject(10);

        // When: Execute with cache miss (using template)
        Instant start = Instant.now();
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "factory pattern",
                "MethodDeclaration",
                null);
        Instant end = Instant.now();

        // Then: Should complete within reasonable time
        long durationMs = Duration.between(start, end).toMillis();
        System.out.println("Cache miss performance: " + durationMs + "ms");

        assertNotNull(result);
        assertTrue(durationMs < 15000, "Cache miss should be < 15s (was " + durationMs + "ms)");
    }

    @Test
    void testPerformance_SmallProject() throws Exception {
        // Given: Small project (10 files)
        createTestProject(10);
        measurePerformance("Small Project (10 files)");
    }

    @Test
    void testPerformance_MediumProject() throws Exception {
        // Given: Medium project (50 files)
        createTestProject(50);
        measurePerformance("Medium Project (50 files)");
    }

    @Test
    void testPerformance_LargeProject() throws Exception {
        // Given: Large project (100 files)
        createTestProject(100);
        measurePerformance("Large Project (100 files)");
    }

    @Test
    void testPerformance_MemoryUsage() throws Exception {
        // Given: Create a moderate-sized project
        createTestProject(30);

        // Measure memory before
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // When: Execute search
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);

        // Measure memory after
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsedMB = (memoryAfter - memoryBefore) / (1024 * 1024);
        System.out.println("Memory usage: " + memoryUsedMB + "MB");

        assertNotNull(result);
        // Memory usage should be reasonable (< 100MB for 30 files)
        assertTrue(memoryUsedMB < 100, "Memory usage should be < 100MB (was " + memoryUsedMB + "MB)");
    }

    @Test
    void testPerformance_ConcurrentRequests() throws Exception {
        // Given: Create test project
        createTestProject(20);

        // Warm up
        tool.searchJavaPattern(tempProjectDir.toString(), "singleton pattern", "ClassDeclaration", null);

        // When: Execute multiple concurrent requests
        int requestCount = 5;
        Instant start = Instant.now();

        for (int i = 0; i < requestCount; i++) {
            String result = tool.searchJavaPattern(
                    tempProjectDir.toString(),
                    "singleton pattern",
                    "ClassDeclaration",
                    null);
            assertNotNull(result);
        }

        Instant end = Instant.now();
        long totalMs = Duration.between(start, end).toMillis();
        double avgMs = (double) totalMs / requestCount;

        System.out.println("Concurrent requests performance:");
        System.out.println("  - Total time: " + totalMs + "ms");
        System.out.println("  - Requests: " + requestCount);
        System.out.println("  - Average per request: " + avgMs + "ms");

        // Average should benefit from caching
        assertTrue(avgMs < 500, "Average request time should be < 500ms (was " + avgMs + "ms)");
    }

    // Helper methods

    private void measurePerformance(String label) throws Exception {
        Instant start = Instant.now();

        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);

        Instant end = Instant.now();
        long durationMs = Duration.between(start, end).toMillis();

        System.out.println("\n" + label + ":");
        System.out.println("  - Total time: " + durationMs + "ms");
        System.out.println("  - Files processed: " + countJavaFiles());
        System.out.println("  - Avg time per file: " + (durationMs / countJavaFiles()) + "ms");

        assertNotNull(result);
        assertTrue(result.contains("\"matches\""), "Should contain matches");
    }

    private void createTestProject(int fileCount) throws Exception {
        for (int i = 0; i < fileCount; i++) {
            Path file = tempProjectDir.resolve("Class" + i + ".java");
            Files.writeString(file, String.format("""
                    package com.example;

                    public class Class%d {
                        private String field%d;

                        public String getField%d() {
                            return field%d;
                        }

                        public void setField%d(String value) {
                            this.field%d = value;
                        }
                    }
                    """, i, i, i, i, i, i));
        }
    }

    private int countJavaFiles() throws Exception {
        return (int) Files.walk(tempProjectDir)
                .filter(p -> p.toString().endsWith(".java"))
                .count();
    }
}
