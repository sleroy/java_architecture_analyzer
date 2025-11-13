package com.analyzer.refactoring.mcp.integration;

import com.analyzer.refactoring.mcp.service.VisitorScriptCache;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for SearchJavaPatternTool.
 * 
 * These tests verify the complete workflow:
 * 1. Request → Cache check
 * 2. Cache miss → Bedrock generation
 * 3. Compilation → Validation
 * 4. OpenRewrite execution → Results
 * 
 * Uses real OpenRewrite parsing and execution, but mocks Bedrock.
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchJavaPatternIntegrationTest {

    @Autowired
    private SearchJavaPatternTool tool;

    @Autowired
    private VisitorScriptCache cache;

    @TempDir
    Path tempProjectDir;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        cache.clear();
    }

    @Test
    void testEndToEndWorkflow_CacheMiss() throws Exception {
        // Given: A Java project with a simple class
        Path javaFile = createTestJavaFile(tempProjectDir, "SimpleClass.java", """
                package com.example;

                public class SimpleClass {
                    private String name;

                    public String getName() {
                        return name;
                    }
                }
                """);

        // When: Search for classes (will use template matching)
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern", // Use pattern that matches a template
                "ClassDeclaration",
                null);

        // Then: Verify results
        assertNotNull(result);
        assertTrue(result.contains("\"matches\""), "Result should contain matches");
    }

    @Test
    void testEndToEndWorkflow_CacheHit() throws Exception {
        // Given: Run the search once to populate cache
        Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                public class TestClass {
                }
                """);

        // First call - cache miss
        String result1 = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);

        // When: Call again with same parameters - cache hit
        String result2 = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);

        // Then: Both results should be similar (same template executed)
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1, result2, "Cache hit should return same result");
    }

    @Test
    void testEndToEndWorkflow_WithMultipleFiles() throws Exception {
        // Given: Multiple Java files
        createTestJavaFile(tempProjectDir, "Class1.java", """
                public class Class1 {
                    private int value;
                }
                """);

        createTestJavaFile(tempProjectDir, "Class2.java", """
                public class Class2 {
                    private String text;
                }
                """);

        createTestJavaFile(tempProjectDir, "Class3.java", """
                public class Class3 {
                    private boolean flag;
                }
                """);

        // When: Search across all files
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);

        // Then: Should execute successfully
        assertNotNull(result);
        assertTrue(result.contains("\"matches\""), "Should contain matches field");
    }

    @Test
    void testEndToEndWorkflow_WithSpecificFiles() throws Exception {
        // Given: Multiple Java files but we only want to search specific ones
        createTestJavaFile(tempProjectDir, "Include.java", """
                public class Include {
                }
                """);

        createTestJavaFile(tempProjectDir, "Exclude.java", """
                public class Exclude {
                }
                """);

        // When: Search only Include.java
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                java.util.List.of("Include.java"));

        // Then: Should execute successfully
        assertNotNull(result);
        assertTrue(result.contains("\"matches\""), "Should contain matches field");
    }

    @Test
    void testEndToEndWorkflow_TemplateUsage() throws Exception {
        // Given: A simple Java class
        Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                public class TestClass {
                }
                """);

        // When: Search with a pattern that matches a template
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "factory pattern", // Matches factory-pattern template
                "MethodDeclaration",
                null);

        // Then: Should execute successfully using template
        assertNotNull(result);
        assertTrue(result.contains("\"matches\""), "Should contain matches field");
    }

    @Test
    void testEndToEndWorkflow_EmptyProject() throws Exception {
        // Given: Empty project directory

        // When: Search in empty project
        String result = tool.searchJavaPattern(
                tempProjectDir.toString(),
                "singleton pattern",
                "ClassDeclaration",
                null);

        // Then: Should return empty matches
        assertNotNull(result);
        assertTrue(result.contains("\"matches\":[]"), "Should have empty matches array");
    }

    // Helper methods

    private Path createTestJavaFile(Path dir, String fileName, String content) throws Exception {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
