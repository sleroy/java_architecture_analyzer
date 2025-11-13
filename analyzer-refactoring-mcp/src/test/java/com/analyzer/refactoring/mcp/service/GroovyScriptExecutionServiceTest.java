package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.script.Bindings;
import javax.script.CompiledScript;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroovyScriptExecutionService.
 */
class GroovyScriptExecutionServiceTest {

    private GroovyScriptExecutionService service;

    @BeforeEach
    void setUp() {
        service = new GroovyScriptExecutionService(30); // 30 second timeout
    }

    @Test
    void testCompileSimpleScript() throws Exception {
        String script = "return 'Hello, World!'";

        CompiledScript compiled = service.compileScript(script);

        assertNotNull(compiled);
    }

    @Test
    void testCompileScriptWithSyntaxError() {
        String script = "this is not valid groovy {{{";

        assertThrows(
                GroovyScriptExecutionService.ScriptCompilationException.class,
                () -> service.compileScript(script));
    }

    @Test
    void testValidateScript() throws Exception {
        String script = "return 42";

        CompiledScript compiled = service.compileScript(script);
        boolean valid = service.validateScript(compiled);

        assertTrue(valid);
    }

    @Test
    void testExecuteScript() throws Exception {
        String script = "return 2 + 2";

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertEquals(4, result);
    }

    @Test
    void testExecuteScriptWithBindings() throws Exception {
        String script = "return x + y";

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();
        bindings.put("x", 10);
        bindings.put("y", 20);

        Object result = service.executeScript(compiled, bindings);

        assertEquals(30, result);
    }

    @Test
    void testExecuteVisitorScript() throws Exception {
        String script = "return 'visitor executed'";

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                .patternDescription("test pattern")
                .nodeType("ClassDeclaration")
                .compiledScript(compiled)
                .sourceCode(script)
                .build();

        Object result = service.executeVisitorScript(visitorScript, bindings);

        assertEquals("visitor executed", result);
    }

    @Test
    void testCompileComplexScript() throws Exception {
        String script = """
                class MyClass {
                    def greet(name) {
                        return "Hello, ${name}!"
                    }
                }

                def obj = new MyClass()
                return obj.greet('Groovy')
                """;

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertEquals("Hello, Groovy!", result.toString());
    }

    @Test
    void testCompileScriptWithImports() throws Exception {
        String script = """
                import java.time.LocalDate

                def today = LocalDate.now()
                return today.getYear()
                """;

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertTrue(result instanceof Integer);
        assertTrue((Integer) result >= 2024);
    }

    @Test
    void testExecuteScriptWithRuntimeError() throws Exception {
        String script = "throw new RuntimeException('Test error')";

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        assertThrows(
                GroovyScriptExecutionService.ScriptExecutionException.class,
                () -> service.executeScript(compiled, bindings));
    }

    @Test
    void testCreateBindings() {
        Bindings bindings = service.createBindings();

        assertNotNull(bindings);
        bindings.put("test", "value");
        assertEquals("value", bindings.get("test"));
    }

    @Test
    void testCompileScriptWithLists() throws Exception {
        String script = """
                def list = [1, 2, 3, 4, 5]
                return list.sum()
                """;

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertEquals(15, result);
    }

    @Test
    void testCompileScriptWithMaps() throws Exception {
        String script = """
                def map = [name: 'John', age: 30]
                return map.name
                """;

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertEquals("John", result);
    }

    @Test
    void testCompileScriptWithClosure() throws Exception {
        String script = """
                def multiply = { a, b -> a * b }
                return multiply(6, 7)
                """;

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertEquals(42, result);
    }

    @Test
    void testValidateScriptWithRuntimeError() throws Exception {
        // This script compiles fine but fails at runtime
        String script = "def x = 1 / 0; return x";

        CompiledScript compiled = service.compileScript(script);

        // Validation should catch runtime errors
        assertThrows(
                GroovyScriptExecutionService.ScriptExecutionException.class,
                () -> service.validateScript(compiled));
    }

    @Test
    void testMultipleExecutionsOfSameScript() throws Exception {
        String script = "return count * 2";

        CompiledScript compiled = service.compileScript(script);

        // Execute multiple times with different bindings
        for (int i = 1; i <= 5; i++) {
            Bindings bindings = service.createBindings();
            bindings.put("count", i);

            Object result = service.executeScript(compiled, bindings);

            assertEquals(i * 2, result);
        }
    }

    @Test
    void testScriptWithStringManipulation() throws Exception {
        String script = """
                def str = 'hello world'
                return str.toUpperCase()
                """;

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertEquals("HELLO WORLD", result);
    }

    @Test
    void testScriptReturnsNull() throws Exception {
        String script = "return null";

        CompiledScript compiled = service.compileScript(script);
        Bindings bindings = service.createBindings();

        Object result = service.executeScript(compiled, bindings);

        assertNull(result);
    }

    @Test
    void testScriptWithConditional() throws Exception {
        String script = """
                if (value > 10) {
                    return 'large'
                } else {
                    return 'small'
                }
                """;

        CompiledScript compiled = service.compileScript(script);

        // Test with large value
        Bindings bindings1 = service.createBindings();
        bindings1.put("value", 20);
        Object result1 = service.executeScript(compiled, bindings1);
        assertEquals("large", result1);

        // Test with small value
        Bindings bindings2 = service.createBindings();
        bindings2.put("value", 5);
        Object result2 = service.executeScript(compiled, bindings2);
        assertEquals("small", result2);
    }
}
