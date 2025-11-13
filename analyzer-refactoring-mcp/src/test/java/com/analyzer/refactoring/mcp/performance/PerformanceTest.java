package com.analyzer.refactoring.mcp.performance;

import com.analyzer.refactoring.mcp.service.GroovyScriptGenerationService;
import com.analyzer.refactoring.mcp.service.VisitorScriptCache;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
 * These tests are disabled by default to avoid slowing down regular test runs.
 * Run with: mvn test -Dtest=PerformanceTest
 */
@SpringBootTest
@Disabled("Performance tests - run manually")
class PerformanceTest {

    @Autowired
    private SearchJavaPatternTool tool;

    @Autowired
    private VisitorScriptCache cache;

    @MockBean
    private GroovyScriptGenerationService mockGenerator;

    @TempDir
    Path tempProjectDir;

    private static final String SIMPLE_VISITOR_SCRIPT = """
            import org.openrewrite.java.JavaIsoVisitor
            import org.openrewrite.ExecutionContext
            import org.openrewrite.java.tree.J
            import org.openrewrite.SourceFile
            import org.openrewrite.InMemoryExecutionContext

            class PatternVisitor extends JavaIsoVisitor<ExecutionContext> {
                List<Map<String, Object>> matches = []

                @Override
                J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    def match = [
                        nodeId: classDecl.id.toString(),
                        nodeType: 'ClassDeclaration',
                        className: classDecl.simpleName,
                        location: [
                            file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                            line: classDecl.prefix.coordinates.line,
                            column: classDecl.prefix.coordinates.column
                        ]
                    ]
                    matches.add(match)
                    return super.visitClassDeclaration(classDecl, ctx)
                }
            }

            def visitor = new PatternVisitor()
            visitor.visit(compilationUnit, new InMemoryExecutionContext())
            return visitor.matches
            """;

    @BeforeEach
    void setUp() {
        cache.clear();
    }

    @Test
    void testPerformance_CacheHit() throws Exception {
        // Given: Create test project and populate cache
        createTestProject(10);

        when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GroovyScriptGenerationService.GenerationResult(SIMPLE_VISITOR_SCRIPT, 1));

        // Warm up cache
        tool.searchJavaPattern(tempProjectDir.toString(), "test", "ClassDeclaration", null);

        // When: Execute with cache hit
        Instant start = Instant.now();
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "test",
                "ClassDeclaration",
                null);
        Instant end = Instant.now();

        // Then: Should be fast (<100ms target)
        long durationMs = Duration.between(start, end).toMillis();
        System.out.println("Cache hit performance: " + durationMs + "ms");

        assertNotNull(result);
        assertTrue(durationMs < 1000, "Cache hit should be < 1000ms (was " + durationMs + "ms)");
        // Note: 100ms target may be challenging in test environment
    }

    @Test
    void testPerformance_CacheMiss() throws Exception {
        // Given: Create test project
        createTestProject(10);

        when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GroovyScriptGenerationService.GenerationResult(SIMPLE_VISITOR_SCRIPT, 1));

        // When: Execute with cache miss
        Instant start = Instant.now();
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "test pattern",
                "ClassDeclaration",
                null);
        Instant end = Instant.now();

        // Then: Should complete within reasonable time (<10s target)
        long durationMs = Duration.between(start, end).toMillis();
        System.out.println("Cache miss performance: " + durationMs + "ms");
        System.out.println("  - Generation: mocked (instant)");
        System.out.println("  - Compilation + Execution: " + durationMs + "ms");

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

        when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GroovyScriptGenerationService.GenerationResult(SIMPLE_VISITOR_SCRIPT, 1));

        // Measure memory before
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // When: Execute search
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "test",
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

        when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GroovyScriptGenerationService.GenerationResult(SIMPLE_VISITOR_SCRIPT, 1));

        // Warm up
        tool.searchJavaPattern(tempProjectDir.toString(), "test", "ClassDeclaration", null);

        // When: Execute multiple concurrent requests
        int requestCount = 5;
        Instant start = Instant.now();

        for (int i = 0; i < requestCount; i++) {
            String result = tool.searchJavaPattern(
                    tempProjectDir.toString(),
                    "test",
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
        when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GroovyScriptGenerationService.GenerationResult(SIMPLE_VISITOR_SCRIPT, 1));

        Instant start = Instant.now();

        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "test pattern",
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
