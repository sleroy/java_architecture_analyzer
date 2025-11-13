package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool.PatternMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.script.CompiledScript;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenRewriteExecutionService using REAL Groovy scripts.
 * No mocks - tests actual visitor execution.
 */
class OpenRewriteExecutionServiceTest {

    private OpenRewriteExecutionService executionService;
    private GroovyScriptExecutionService scriptService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        executionService = new OpenRewriteExecutionService();
        scriptService = new GroovyScriptExecutionService(30);
    }

    @Test
    void testExecuteVisitorOnProject_WithSimpleJavaFile() throws Exception {
        // Create a simple Java file
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, """
                package com.example;

                public class TestClass {
                    private String name;

                    public String getName() {
                        return name;
                    }
                }
                """);

        // Use REAL Groovy script that finds all classes
        String groovyScript = createClassFinderScript();
        CompiledScript compiled = scriptService.compileScript(groovyScript);

        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription("Find all classes")
                .nodeType("ClassDeclaration")
                .projectPath(tempDir.toString())
                .compiledScript(compiled)
                .sourceCode(groovyScript)
                .build();

        // Execute
        List<PatternMatch> matches = executionService.executeVisitorOnProject(
                visitorScript, tempDir.toString(), null);

        // Verify - should find TestClass
        assertNotNull(matches);
        assertTrue(matches.size() > 0, "Should find at least one class");

        PatternMatch match = matches.get(0);
        assertNotNull(match.getNodeId());
        assertEquals("ClassDeclaration", match.getNodeType());
        assertEquals("TestClass", match.getClassName());
        assertNotNull(match.getLocation());
    }

    @Test
    void testExecuteVisitorOnProject_WithMultipleFiles() throws Exception {
        // Create multiple Java files
        Files.writeString(tempDir.resolve("Class1.java"), """
                package com.example;
                public class Class1 {
                    private int value;
                }
                """);

        Files.writeString(tempDir.resolve("Class2.java"), """
                package com.example;
                public class Class2 {
                    private String text;
                }
                """);

        // Use REAL Groovy script
        String groovyScript = createClassFinderScript();
        CompiledScript compiled = scriptService.compileScript(groovyScript);

        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription("Find all classes")
                .nodeType("ClassDeclaration")
                .projectPath(tempDir.toString())
                .compiledScript(compiled)
                .sourceCode(groovyScript)
                .build();

        // Execute
        List<PatternMatch> matches = executionService.executeVisitorOnProject(
                visitorScript, tempDir.toString(), null);

        // Verify - should find 2 classes
        assertNotNull(matches);
        assertEquals(2, matches.size(), "Should find both classes");

        List<String> classNames = matches.stream()
                .map(PatternMatch::getClassName)
                .toList();
        assertTrue(classNames.contains("Class1"));
        assertTrue(classNames.contains("Class2"));
    }

    @Test
    void testExecuteVisitorOnProject_WithSpecificFilePaths() throws Exception {
        // Create Java files
        Files.writeString(tempDir.resolve("Include.java"), """
                public class Include {
                }
                """);

        Files.writeString(tempDir.resolve("Exclude.java"), """
                public class Exclude {
                }
                """);

        // Use REAL Groovy script
        String groovyScript = createClassFinderScript();
        CompiledScript compiled = scriptService.compileScript(groovyScript);

        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription("Find classes")
                .nodeType("ClassDeclaration")
                .projectPath(tempDir.toString())
                .compiledScript(compiled)
                .sourceCode(groovyScript)
                .build();

        // Execute with specific file path
        List<String> filePaths = List.of("Include.java");
        List<PatternMatch> matches = executionService.executeVisitorOnProject(
                visitorScript, tempDir.toString(), filePaths);

        // Verify - should only find Include class
        assertNotNull(matches);
        assertEquals(1, matches.size(), "Should only process Include.java");
        assertEquals("Include", matches.get(0).getClassName());
    }

    @Test
    void testExecuteVisitorOnProject_EmptyProject() throws Exception {
        // Empty directory - no Java files

        String groovyScript = createClassFinderScript();
        CompiledScript compiled = scriptService.compileScript(groovyScript);

        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription("Find classes")
                .nodeType("ClassDeclaration")
                .projectPath(tempDir.toString())
                .compiledScript(compiled)
                .sourceCode(groovyScript)
                .build();

        // Execute
        List<PatternMatch> matches = executionService.executeVisitorOnProject(
                visitorScript, tempDir.toString(), null);

        // Verify - should return empty list
        assertNotNull(matches);
        assertTrue(matches.isEmpty(), "Should find no classes in empty project");
    }

    @Test
    void testExecuteVisitorOnProject_InvalidPath() {
        String groovyScript = createClassFinderScript();

        try {
            CompiledScript compiled = scriptService.compileScript(groovyScript);

            OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                    .patternDescription("Find classes")
                    .nodeType("ClassDeclaration")
                    .projectPath("/invalid/path")
                    .compiledScript(compiled)
                    .sourceCode(groovyScript)
                    .build();

            // Execute with invalid path - should throw
            assertThrows(OpenRewriteExecutionService.ExecutionException.class, () -> {
                executionService.executeVisitorOnProject(
                        visitorScript, "/invalid/path/that/does/not/exist", null);
            });
        } catch (Exception e) {
            fail("Setup failed: " + e.getMessage());
        }
    }

    @Test
    void testExecuteVisitorOnProject_FindMethods() throws Exception {
        // Create a Java file with methods
        Files.writeString(tempDir.resolve("Service.java"), """
                public class Service {
                    public void doSomething() {
                    }

                    private void helper() {
                    }
                }
                """);

        // Use REAL Groovy script that finds methods
        String groovyScript = createMethodFinderScript();
        CompiledScript compiled = scriptService.compileScript(groovyScript);

        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription("Find all methods")
                .nodeType("MethodDeclaration")
                .projectPath(tempDir.toString())
                .compiledScript(compiled)
                .sourceCode(groovyScript)
                .build();

        // Execute
        List<PatternMatch> matches = executionService.executeVisitorOnProject(
                visitorScript, tempDir.toString(), null);

        // Verify - should find methods (excluding constructor)
        assertNotNull(matches);
        assertTrue(matches.size() >= 2, "Should find at least 2 methods");
    }

    // Helper methods - REAL Groovy scripts

    private String createClassFinderScript() {
        return """
                import org.openrewrite.java.JavaIsoVisitor
                import org.openrewrite.ExecutionContext
                import org.openrewrite.java.tree.J
                import org.openrewrite.SourceFile
                import org.openrewrite.InMemoryExecutionContext

                class ClassFinderVisitor extends JavaIsoVisitor<ExecutionContext> {
                    List<Map<String, Object>> matches = []

                    @Override
                    J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        def match = [
                            nodeId: classDecl.id.toString(),
                            nodeType: 'ClassDeclaration',
                            className: classDecl.simpleName,
                            location: [
                                file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                                line: 0,
                                column: 0
                            ]
                        ]
                        matches.add(match)
                        return super.visitClassDeclaration(classDecl, ctx)
                    }
                }

                def visitor = new ClassFinderVisitor()
                visitor.visit(compilationUnit, new InMemoryExecutionContext())
                return visitor.matches
                """;
    }

    private String createMethodFinderScript() {
        return """
                import org.openrewrite.java.JavaIsoVisitor
                import org.openrewrite.ExecutionContext
                import org.openrewrite.java.tree.J
                import org.openrewrite.SourceFile
                import org.openrewrite.InMemoryExecutionContext

                class MethodFinderVisitor extends JavaIsoVisitor<ExecutionContext> {
                    List<Map<String, Object>> matches = []

                    @Override
                    J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        if (!method.isConstructor()) {
                            def classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class)
                            def match = [
                                nodeId: method.id.toString(),
                                nodeType: 'MethodDeclaration',
                                className: classDecl?.simpleName ?: 'Unknown',
                                methodName: method.simpleName,
                                location: [
                                    file: getCursor().firstEnclosingOrThrow(SourceFile.class).sourcePath.toString(),
                                    line: 0,
                                    column: 0
                                ]
                            ]
                            matches.add(match)
                        }
                        return super.visitMethodDeclaration(method, ctx)
                    }
                }

                def visitor = new MethodFinderVisitor()
                visitor.visit(compilationUnit, new InMemoryExecutionContext())
                return visitor.matches
                """;
    }
}
