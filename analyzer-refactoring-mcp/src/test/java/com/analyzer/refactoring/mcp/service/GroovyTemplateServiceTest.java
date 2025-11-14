package com.analyzer.refactoring.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;

import javax.script.CompiledScript;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for GroovyTemplateService.
 * 
 * These tests verify:
 * - Template loading and caching
 * - Parameter binding and execution
 * - Error handling
 * - Cache management
 */
@SpringBootTest
@ActiveProfiles("test")
class GroovyTemplateServiceTest {

    @Autowired
    private GroovyTemplateService templateService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void testLoadTemplate() throws Exception {
        // Should successfully load and compile a template
        CompiledScript compiled = templateService.loadTemplate("annotation-class-finder.groovy");

        assertThat(compiled).isNotNull();
        assertThat(compiled.getEngine()).isNotNull();
    }

    @Test
    void testLoadTemplateNotFound() {
        // Should throw TemplateException for non-existent template
        assertThatThrownBy(() -> templateService.loadTemplate("non-existent-template.groovy"))
                .isInstanceOf(GroovyTemplateService.TemplateException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void testTemplateCaching() throws Exception {
        // First load
        CompiledScript first = templateService.loadTemplate("annotation-class-finder.groovy");

        // Second load should return cached version
        CompiledScript second = templateService.loadTemplate("annotation-class-finder.groovy");

        // Verify caching (should be same instance from cache)
        Map<String, Object> stats = templateService.getCacheStats();
        assertThat(stats.get("cachedTemplates")).isEqualTo(1);
        assertThat(stats.get("templateNames")).asList().contains("annotation-class-finder.groovy");
    }

    @Test
    void testClearCache() throws Exception {
        // Load a template
        templateService.loadTemplate("annotation-class-finder.groovy");

        assertThat(templateService.getCacheStats().get("cachedTemplates")).isEqualTo(1);

        // Clear cache
        templateService.clearCache();

        assertThat(templateService.getCacheStats().get("cachedTemplates")).isEqualTo(0);
    }

    @Test
    void testParameterBindingWithSimpleScript() throws Exception {
        // Create a simple test script that uses parameters from binding
        String testScript = """
                String message = greeting + " " + name
                return message
                """;

        // Compile script
        CompiledScript compiled = compileTestScript(testScript);

        // Execute with parameters
        String result = templateService.executeTemplate(
                compiled,
                Map.of("greeting", "Hello", "name", "World"),
                null // No compilation unit needed for this test
        );

        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void testParameterBindingWithTypedParameters() throws Exception {
        // Test that parameters maintain their types through binding
        String testScript = """
                Integer sum = count + 10
                return sum
                """;

        CompiledScript compiled = compileTestScript(testScript);

        Integer result = templateService.executeTemplate(
                compiled,
                Map.of("count", 5),
                null);

        assertThat(result).isEqualTo(15);
    }

    @Test
    void testParameterBindingWithList() throws Exception {
        // Test that list parameters work correctly
        String testScript = """
                return items.size()
                """;

        CompiledScript compiled = compileTestScript(testScript);

        Integer result = templateService.executeTemplate(
                compiled,
                Map.of("items", List.of("a", "b", "c")),
                null);

        assertThat(result).isEqualTo(3);
    }

    @Test
    void testExecuteTemplateWithNullParameters() throws Exception {
        // Should handle null parameters gracefully
        String testScript = """
                return "success"
                """;

        CompiledScript compiled = compileTestScript(testScript);

        String result = templateService.executeTemplate(compiled, null, null);

        assertThat(result).isEqualTo("success");
    }

    @Test
    void testExecuteTemplateWithCompilationUnit() throws Exception {
        // Test that compilationUnit is automatically added to binding
        String testScript = """
                if (compilationUnit == null) {
                    return "NO_CU"
                }
                return "HAS_CU"
                """;

        CompiledScript compiled = compileTestScript(testScript);

        // Execute without compilation unit
        String resultWithout = templateService.executeTemplate(compiled, null, null);
        assertThat(resultWithout).isEqualTo("NO_CU");

        // Execute with a mock compilation unit
        Object mockCU = "mock-compilation-unit";
        String resultWith = templateService.executeTemplate(compiled, null, mockCU);
        assertThat(resultWith).isEqualTo("HAS_CU");
    }

    /**
     * Helper method to compile a test script.
     */
    private CompiledScript compileTestScript(String script) throws Exception {
        // Use the GroovyScriptExecutionService to compile
        GroovyScriptExecutionService executionService = new GroovyScriptExecutionService(30);
        return executionService.compileScript(script);
    }
}
