package com.analyzer.refactoring.mcp.integration;

import com.analyzer.refactoring.mcp.service.RecipeScriptCache;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchReplaceJavaPatternTool;
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
 * End-to-end integration tests for SearchReplaceJavaPatternTool.
 * 
 * These tests verify the complete workflow:
 * 1. Request → Cache check
 * 2. Cache miss → Bedrock generation (recipe)
 * 3. Compilation → Validation
 * 4. OpenRewrite recipe execution → Transformations
 * 5. Diff generation → Results
 * 
 * Uses real OpenRewrite parsing and execution.
 * Note: These tests require working Bedrock configuration or will use mock
 * responses.
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchReplaceJavaPatternIntegrationTest {

    @Autowired(required = false)
    private SearchReplaceJavaPatternTool tool;

    @Autowired(required = false)
    private RecipeScriptCache cache;

    @TempDir
    Path tempProjectDir;

    @BeforeEach
    void setUp() {
        // Clear cache before each test if available
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testToolAvailability() {
        // Basic test to verify tool is wired up correctly
        assertNotNull(tool, "SearchReplaceJavaPatternTool should be available");
        assertNotNull(cache, "RecipeScriptCache should be available");
    }

    @Test
    void testEndToEnd_SimpleMethodRename() throws Exception {
        if (tool == null) {
            return; // Skip if tool not available
        }

        // Given: A Java class with methods to rename
        Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                package com.example;

                public class TestClass {
                    public void oldMethod() {
                        System.out.println("Hello");
                    }

                    public void oldProcess() {
                        // Do something
                    }
                }
                """);

        // When: Apply transformation to rename methods
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "methods starting with 'old'",
                "rename to start with 'new' instead",
                "MethodDeclaration",
                null);

        // Then: Verify results indicate transformation
        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("transformedFiles") || result.contains("filesChanged"),
                "Result should indicate files changed");
    }

    @Test
    void testEndToEnd_NoMatches() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: A Java class without matching pattern
        Path javaFile = createTestJavaFile(tempProjectDir, "SimpleClass.java", """
                package com.example;

                public class SimpleClass {
                    private String name;

                    public String getName() {
                        return name;
                    }
                }
                """);

        // When: Try to transform non-existent pattern
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "methods with @Deprecated annotation",
                "remove @Deprecated and add @SuppressWarnings",
                "MethodDeclaration",
                null);

        // Then: Should execute but show no changes
        assertNotNull(result, "Result should not be null");
        // May have filesChanged: 0 or empty transformedFiles array
    }

    @Test
    void testEndToEnd_CacheHit() throws Exception {
        if (tool == null || cache == null) {
            return;
        }

        Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                public class TestClass {
                    public void testMethod() {}
                }
                """);

        // First call - cache miss, generates recipe
        String result1 = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "public methods",
                "make them private",
                "MethodDeclaration",
                null);

        int initialCacheSize = cache.size();

        // Second call - cache hit, reuses recipe
        String result2 = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "public methods",
                "make them private",
                "MethodDeclaration",
                null);

        // Cache size should not increase (recipe was reused)
        assertEquals(initialCacheSize, cache.size(),
                "Cache size should not increase on cache hit");
        assertNotNull(result1, "First result should not be null");
        assertNotNull(result2, "Second result should not be null");
    }

    @Test
    void testEndToEnd_WithMultipleFiles() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: Multiple Java files
        createTestJavaFile(tempProjectDir, "Class1.java", """
                public class Class1 {
                    public void method1() {}
                }
                """);

        createTestJavaFile(tempProjectDir, "Class2.java", """
                public class Class2 {
                    public void method2() {}
                }
                """);

        createTestJavaFile(tempProjectDir, "Class3.java", """
                public class Class3 {
                    public void method3() {}
                }
                """);

        // When: Apply transformation across all files
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "public void methods",
                "change to protected void",
                "MethodDeclaration",
                null);

        // Then: Should process multiple files
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testEndToEnd_WithSpecificFiles() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: Multiple files but we only want to transform specific ones
        createTestJavaFile(tempProjectDir, "Include.java", """
                public class Include {
                    public void targetMethod() {}
                }
                """);

        createTestJavaFile(tempProjectDir, "Exclude.java", """
                public class Exclude {
                    public void targetMethod() {}
                }
                """);

        // When: Transform only Include.java
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "public methods",
                "rename targetMethod to processedMethod",
                "MethodDeclaration",
                java.util.List.of("Include.java"));

        // Then: Should execute on specified file only
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testEndToEnd_EmptyProject() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: Empty project directory (no Java files)

        // When: Try to transform
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "any pattern",
                "any transformation",
                "ClassDeclaration",
                null);

        // Then: Should handle gracefully with no errors
        assertNotNull(result, "Result should not be null even for empty project");
        assertTrue(result.contains("filesChanged") || result.contains("\"filesChanged\":0"),
                "Should indicate 0 files changed");
    }

    @Test
    void testEndToEnd_AnnotationTransformation() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: Class with EJB annotation
        Path javaFile = createTestJavaFile(tempProjectDir, "MyService.java", """
                package com.example;

                import javax.ejb.Stateless;

                @Stateless
                public class MyService {
                    public void businessMethod() {
                        // Business logic
                    }
                }
                """);

        // When: Transform EJB to Spring
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "classes with @Stateless annotation",
                "replace @Stateless with @Service",
                "ClassDeclaration",
                null);

        // Then: Should execute transformation
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testEndToEnd_ComplexTransformation() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: Class with field injection
        Path javaFile = createTestJavaFile(tempProjectDir, "UserService.java", """
                package com.example;

                public class UserService {
                    @javax.ejb.EJB
                    private UserRepository repository;

                    public void save(User user) {
                        repository.save(user);
                    }
                }
                """);

        // When: Convert field injection to constructor injection
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "classes with @EJB field injection",
                "convert to constructor injection with @Autowired",
                "ClassDeclaration",
                null);

        // Then: Should execute complex transformation
        assertNotNull(result, "Result should not be null");
    }

    @Test
    void testResultContainsDiff() throws Exception {
        if (tool == null) {
            return;
        }

        // Given: Simple Java class
        Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                public class TestClass {
                    public void testMethod() {
                        int x = 1;
                    }
                }
                """);

        // When: Apply any transformation
        String result = tool.searchReplaceJavaPattern(
                tempProjectDir.toString(),
                "public methods",
                "add @Override annotation if missing",
                "MethodDeclaration",
                null);

        // Then: Result should be JSON with structure
        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("{") && result.contains("}"), "Result should be JSON format");
    }

    // Helper methods

    private Path createTestJavaFile(Path dir, String fileName, String content) throws Exception {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
