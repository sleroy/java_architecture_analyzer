package com.analyzer.refactoring.mcp.integration;

import com.analyzer.refactoring.mcp.service.GroovyScriptGenerationService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.analyzer.refactoring.mcp.service.VisitorScriptCache;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
class SearchJavaPatternIntegrationTest {

        @Autowired
        private SearchJavaPatternTool tool;

        @Autowired
        private VisitorScriptCache cache;

        @MockBean
        private GroovyScriptGenerationService mockGenerator;

        @MockBean
        private PatternMatcherAgent mockMatcher;

        @TempDir
        Path tempProjectDir;

        @BeforeEach
        void setUp() {
                // Clear cache before each test
                cache.clear();

                // Mock pattern matcher to return empty (no AI match - fall through to
                // generation)
                when(mockMatcher.findBestMatch(any(), any(), any()))
                                .thenReturn(Optional.empty());
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

                // Mock Bedrock to return a valid visitor script
                String groovyScript = """
                                import org.openrewrite.java.JavaIsoVisitor
                                import org.openrewrite.ExecutionContext
                                import org.openrewrite.java.tree.J
                                import org.openrewrite.SourceFile
                                import org.openrewrite.InMemoryExecutionContext

                                class PatternVisitor extends JavaIsoVisitor<ExecutionContext> {
                                    List<Map<String, Object>> matches = []

                                    @Override
                                    J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                        // Match all classes
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

                when(mockGenerator.generateVisitorScript(
                                anyString(), anyString(), anyString(), any()))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(groovyScript, 1));

                // When: Search for classes
                String result = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "all classes",
                                "ClassDeclaration",
                                null);

                // Then: Verify results
                assertNotNull(result);
                assertTrue(result.contains("\"matches\""), "Result should contain matches");
                assertTrue(result.contains("\"scriptGenerated\":true"), "Script should be generated");
                assertTrue(result.contains("SimpleClass"), "Should find SimpleClass");

                // Verify Bedrock was called
                verify(mockGenerator, times(1)).generateVisitorScript(
                                anyString(), anyString(), anyString(), any());
        }

        @Test
        void testEndToEndWorkflow_CacheHit() throws Exception {
                // Given: Run the search once to populate cache
                Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                                public class TestClass {
                                }
                                """);

                String groovyScript = createSimpleVisitorScript();
                when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(groovyScript, 1));

                // First call - cache miss
                String result1 = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "test pattern",
                                "ClassDeclaration",
                                null);

                // When: Call again with same parameters - cache hit
                String result2 = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "test pattern",
                                "ClassDeclaration",
                                null);

                // Then: Verify Bedrock was only called once (cache hit on second call)
                verify(mockGenerator, times(1)).generateVisitorScript(
                                anyString(), anyString(), anyString(), any());

                // Both results should be similar (same script executed)
                assertNotNull(result1);
                assertNotNull(result2);
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

                String groovyScript = createSimpleVisitorScript();
                when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(groovyScript, 1));

                // When: Search across all files
                String result = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "all classes",
                                "ClassDeclaration",
                                null);

                // Then: Should find all 3 classes
                assertNotNull(result);
                assertTrue(result.contains("Class1"), "Should find Class1");
                assertTrue(result.contains("Class2"), "Should find Class2");
                assertTrue(result.contains("Class3"), "Should find Class3");
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

                String groovyScript = createSimpleVisitorScript();
                when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(groovyScript, 1));

                // When: Search only Include.java
                String result = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "specific class",
                                "ClassDeclaration",
                                java.util.List.of("Include.java"));

                // Then: Should only find Include class
                assertNotNull(result);
                assertTrue(result.contains("Include"), "Should find Include class");
                assertFalse(result.contains("Exclude"), "Should NOT find Exclude class");
        }

        @Test
        void testEndToEndWorkflow_GenerationRetry() throws Exception {
                // Given: Bedrock fails first time, succeeds second time
                Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                                public class TestClass {
                                }
                                """);

                String validScript = createSimpleVisitorScript();

                when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                                .thenThrow(new GroovyScriptGenerationService.ScriptGenerationException("Bedrock error"))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(validScript, 2));

                // When/Then: Should throw exception on first attempt
                assertThrows(Exception.class, () -> {
                        tool.searchJavaPattern(
                                        tempProjectDir.toString(),
                                        "pattern",
                                        "ClassDeclaration",
                                        null);
                });
        }

        @Test
        void testEndToEndWorkflow_InvalidScript() throws Exception {
                // Given: Bedrock returns invalid Groovy script
                Path javaFile = createTestJavaFile(tempProjectDir, "TestClass.java", """
                                public class TestClass {
                                }
                                """);

                String invalidScript = "this is not valid groovy {{{";
                when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(invalidScript, 1));

                // When/Then: Should fail with compilation error
                String result = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "pattern",
                                "ClassDeclaration",
                                null);

                // Result should contain error
                assertTrue(result.contains("\"error\""), "Should contain error in result");
        }

        @Test
        void testEndToEndWorkflow_EmptyProject() throws Exception {
                // Given: Empty project directory

                String groovyScript = createSimpleVisitorScript();
                when(mockGenerator.generateVisitorScript(anyString(), anyString(), anyString(), any()))
                                .thenReturn(new GroovyScriptGenerationService.GenerationResult(groovyScript, 1));

                // When: Search in empty project
                String result = tool.searchJavaPattern(
                                tempProjectDir.toString(),
                                "pattern",
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

        private String createSimpleVisitorScript() {
                return """
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
        }
}
