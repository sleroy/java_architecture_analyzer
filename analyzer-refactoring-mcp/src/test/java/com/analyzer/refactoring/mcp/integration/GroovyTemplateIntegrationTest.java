package com.analyzer.refactoring.mcp.integration;

import com.analyzer.refactoring.mcp.service.GroovyTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.script.CompiledScript;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Groovy templates with real Java code parsing.
 * 
 * These tests verify that each template:
 * 1. Compiles successfully
 * 2. Executes without errors on real Java AST
 * 3. Returns correct match structure
 * 4. Handles parameter binding correctly
 * 5. Extracts accurate location information
 */
@SpringBootTest
@ActiveProfiles("test")
class GroovyTemplateIntegrationTest {

    @Autowired
    private GroovyTemplateService templateService;

    @TempDir
    Path tempDir;

    /**
     * Test annotation-class-finder.groovy template with real Java code.
     */
    @Test
    void testAnnotationClassFinderTemplate() throws Exception {
        // Create test Java file with @Stateless annotation
        String javaCode = """
                package com.example;

                import javax.ejb.Stateless;
                import javax.ejb.Local;

                @Stateless
                @Local(UserService.class)
                public class UserServiceBean implements UserService {

                    public String getUser(String id) {
                        return "User: " + id;
                    }
                }
                """;

        // Parse Java code
        J.CompilationUnit cu = parseJavaCode(javaCode);

        // Load and execute template
        CompiledScript template = templateService.loadTemplate("annotation-class-finder.groovy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = templateService.executeTemplate(
                template,
                Map.of("annotationName", "Stateless"),
                cu);

        // Verify results
        assertThat(matches).hasSize(1);

        Map<String, Object> match = matches.get(0);
        assertThat(match.get("nodeType")).isEqualTo("ClassDeclaration");
        assertThat(match.get("className")).isEqualTo("UserServiceBean");
        assertThat(match.get("annotations")).asList().hasSize(2);
        assertThat(match.get("modifiers")).asList().contains("Public");
        assertThat(match.get("implements")).asList().contains("UserService");

        // Verify location information
        @SuppressWarnings("unchecked")
        Map<String, Object> location = (Map<String, Object>) match.get("location");
        assertThat(location).isNotNull();
        assertThat(location.get("line")).isNotNull();
        assertThat(location.get("column")).isNotNull();
    }

    /**
     * Test annotation-class-finder.groovy with no matches.
     */
    @Test
    void testAnnotationClassFinderNoMatches() throws Exception {
        String javaCode = """
                package com.example;

                public class PlainClass {
                    public void doSomething() {}
                }
                """;

        J.CompilationUnit cu = parseJavaCode(javaCode);

        CompiledScript template = templateService.loadTemplate("annotation-class-finder.groovy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = templateService.executeTemplate(
                template,
                Map.of("annotationName", "Stateless"),
                cu);

        assertThat(matches).isEmpty();
    }

    /**
     * Test interface-implementation-finder.groovy template (FIXES J.TypeTree
     * issue).
     */
    @Test
    void testInterfaceImplementationFinderTemplate() throws Exception {
        String javaCode = """
                package com.example;

                import javax.jms.MessageListener;
                import javax.jms.Message;

                public class MessageProcessor implements MessageListener {

                    @Override
                    public void onMessage(Message message) {
                        // Process message
                    }
                }
                """;

        J.CompilationUnit cu = parseJavaCode(javaCode);

        // Load and execute template - this verifies TypeTree import is correct!
        CompiledScript template = templateService.loadTemplate("interface-implementation-finder.groovy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = templateService.executeTemplate(
                template,
                Map.of("interfaceName", "MessageListener"),
                cu);

        // Verify results
        assertThat(matches).hasSize(1);

        Map<String, Object> match = matches.get(0);
        assertThat(match.get("nodeType")).isEqualTo("ClassDeclaration");
        assertThat(match.get("className")).isEqualTo("MessageProcessor");
        assertThat(match.get("implements")).asList().contains("MessageListener");

        // Verify no J.TypeTree compilation error occurred
        assertThat(match.get("nodeId")).isNotNull();
    }

    /**
     * Test interface-implementation-finder.groovy with multiple interfaces.
     */
    @Test
    void testInterfaceFinderMultipleInterfaces() throws Exception {
        String javaCode = """
                package com.example;

                import java.io.Serializable;
                import java.lang.Runnable;

                public class MultiInterfaceClass implements Serializable, Runnable {
                    public void run() {}
                }
                """;

        J.CompilationUnit cu = parseJavaCode(javaCode);

        CompiledScript template = templateService.loadTemplate("interface-implementation-finder.groovy");

        // Search for Serializable
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> serializableMatches = templateService.executeTemplate(
                template,
                Map.of("interfaceName", "Serializable"),
                cu);

        assertThat(serializableMatches).hasSize(1);
        assertThat(serializableMatches.get(0).get("implements")).asList().hasSize(2);

        // Search for Runnable
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runnableMatches = templateService.executeTemplate(
                template,
                Map.of("interfaceName", "Runnable"),
                cu);

        assertThat(runnableMatches).hasSize(1);
    }

    /**
     * Test annotation-method-finder.groovy template.
     */
    @Test
    void testAnnotationMethodFinderTemplate() throws Exception {
        String javaCode = """
                package com.example;

                import org.springframework.transaction.annotation.Transactional;

                public class UserService {

                    @Transactional
                    public void saveUser(String name) {
                        // Save user
                    }

                    @Transactional(readOnly = true)
                    public String getUser(String id) {
                        return "User: " + id;
                    }

                    public void nonTransactionalMethod() {
                        // No annotation
                    }
                }
                """;

        J.CompilationUnit cu = parseJavaCode(javaCode);

        CompiledScript template = templateService.loadTemplate("annotation-method-finder.groovy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = templateService.executeTemplate(
                template,
                Map.of("annotationName", "Transactional"),
                cu);

        // Should find 2 methods with @Transactional
        assertThat(matches).hasSize(2);

        // Verify first match
        Map<String, Object> firstMatch = matches.get(0);
        assertThat(firstMatch.get("nodeType")).isEqualTo("MethodDeclaration");
        assertThat(firstMatch.get("className")).isEqualTo("UserService");
        assertThat(firstMatch.get("methodName")).isIn("saveUser", "getUser");
        assertThat(firstMatch.get("returnType")).isNotNull();
        assertThat(firstMatch.get("parameterTypes")).isNotNull();
        assertThat(firstMatch.get("parameterCount")).isNotNull();

        // Verify location
        @SuppressWarnings("unchecked")
        Map<String, Object> location = (Map<String, Object>) firstMatch.get("location");
        assertThat(location.get("line")).isNotNull();
        assertThat(location.get("column")).isNotNull();
    }

    /**
     * Test annotation-method-finder.groovy with different modifiers.
     */
    @Test
    void testAnnotationMethodFinderWithModifiers() throws Exception {
        String javaCode = """
                package com.example;

                public class TestClass {

                    @Override
                    public String toString() {
                        return "test";
                    }

                    @Override
                    protected int hashCode() {
                        return 42;
                    }
                }
                """;

        J.CompilationUnit cu = parseJavaCode(javaCode);

        CompiledScript template = templateService.loadTemplate("annotation-method-finder.groovy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = templateService.executeTemplate(
                template,
                Map.of("annotationName", "Override"),
                cu);

        assertThat(matches).hasSize(2);

        // Verify modifiers are extracted
        for (Map<String, Object> match : matches) {
            assertThat(match.get("modifiers")).isNotNull();
            @SuppressWarnings("unchecked")
            List<String> modifiers = (List<String>) match.get("modifiers");
            assertThat(modifiers).isNotEmpty();
        }
    }

    /**
     * Test all templates compile successfully.
     */
    @Test
    void testAllTemplatesCompile() throws Exception {
        // These should all compile without errors
        assertThatNoException().isThrownBy(() -> templateService.loadTemplate("annotation-class-finder.groovy"));

        assertThatNoException()
                .isThrownBy(() -> templateService.loadTemplate("interface-implementation-finder.groovy"));

        assertThatNoException().isThrownBy(() -> templateService.loadTemplate("annotation-method-finder.groovy"));
    }

    /**
     * Test that interface-implementation-finder correctly uses TypeTree (not
     * J.TypeTree).
     * This verifies the fix for the compilation error.
     */
    @Test
    void testInterfaceFinderUsesCorrectTypeTreeImport() throws Exception {
        // This test verifies the template compiles, which means it's using
        // the correct "import org.openrewrite.java.tree.TypeTree"
        // instead of the incorrect "J.TypeTree"

        String javaCode = """
                package com.example;

                public class TestClass implements Runnable {
                    public void run() {}
                }
                """;

        J.CompilationUnit cu = parseJavaCode(javaCode);

        // This will fail if J.TypeTree is used (compilation error)
        CompiledScript template = templateService.loadTemplate("interface-implementation-finder.groovy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = templateService.executeTemplate(
                template,
                Map.of("interfaceName", "Runnable"),
                cu);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("implements")).asList().isNotEmpty();
    }

    /**
     * Helper method to parse Java code into a compilation unit.
     */
    private J.CompilationUnit parseJavaCode(String javaCode) throws Exception {
        // Write Java code to temp file
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.writeString(javaFile, javaCode);

        // Parse with OpenRewrite
        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
        });
        JavaParser parser = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(false)
                .build();

        List<SourceFile> parsed = parser.parse(List.of(javaFile), null, ctx)
                .toList();

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0)).isInstanceOf(J.CompilationUnit.class);

        return (J.CompilationUnit) parsed.get(0);
    }
}
