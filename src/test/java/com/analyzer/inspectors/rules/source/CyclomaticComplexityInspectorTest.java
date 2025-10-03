package com.analyzer.inspectors.rules.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubClazz;
import com.analyzer.test.stubs.StubResourceLocation;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CyclomaticComplexityInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private CyclomaticComplexityInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        inspector = new CyclomaticComplexityInspector(stubResourceResolver);
    }

    @Test
    void testGetName() {
        assertEquals("cyclomatic-complexity", inspector.getName());
    }

    @Test
    void testGetColumnName() {
        assertEquals("Cyclomatic Complexity", inspector.getColumnName());
    }

    @Test
    void testSimpleMethodComplexity() throws IOException {
        // Single method with no decision points - complexity should be 1
        String sourceCode = """
            public class TestClass {
                public void simpleMethod() {
                    System.out.println("Hello World");
                    int x = 5;
                    String message = "Simple";
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(1, result.getValue());
        assertEquals("cyclomatic-complexity", result.getInspectorName());
    }

    @Test
    void testMethodWithIfStatement() throws IOException {
        // Method with if statement - complexity should be 2 (1 + 1 for if)
        String sourceCode = """
            public class TestClass {
                public void methodWithIf(boolean condition) {
                    if (condition) {
                        System.out.println("True branch");
                    }
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getValue());
    }

    @Test
    void testMethodWithIfElse() throws IOException {
        // Method with if-else - complexity should be 2 (1 + 1 for if)
        String sourceCode = """
            public class TestClass {
                public String methodWithIfElse(boolean condition) {
                    if (condition) {
                        return "true";
                    } else {
                        return "false";
                    }
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getValue());
    }

    @Test
    void testMethodWithNestedIf() throws IOException {
        // Method with nested if statements - complexity should be 3 (1 + 1 + 1)
        String sourceCode = """
            public class TestClass {
                public void nestedIf(boolean a, boolean b) {
                    if (a) {
                        if (b) {
                            System.out.println("Both true");
                        }
                    }
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(3, result.getValue());
    }

    @Test
    void testMethodWithLoops() throws IOException {
        // Method with various loops - complexity should be 4 (1 + 1 for each loop)
        String sourceCode = """
            public class TestClass {
                public void methodWithLoops() {
                    for (int i = 0; i < 10; i++) {
                        System.out.println(i);
                    }
                    
                    while (true) {
                        break;
                    }
                    
                    do {
                        System.out.println("do-while");
                    } while (false);
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(4, result.getValue());
    }

    @Test
    void testMethodWithSwitch() throws IOException {
        // Method with switch statement - complexity should be 4 (1 + 1 for switch + 1 for each case)
        String sourceCode = """
            public class TestClass {
                public String methodWithSwitch(int value) {
                    switch (value) {
                        case 1:
                            return "one";
                        case 2:
                            return "two";
                        default:
                            return "other";
                    }
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(4, result.getValue()); // 1 + 1 (switch) + 1 (case 1) + 1 (case 2) + 1 (default)
    }

    @Test
    void testMethodWithTryCatch() throws IOException {
        // Method with try-catch - complexity should be 2 (1 + 1 for catch)
        String sourceCode = """
            public class TestClass {
                public void methodWithTryCatch() {
                    try {
                        riskyOperation();
                    } catch (Exception e) {
                        handleError(e);
                    }
                }
                
                private void riskyOperation() throws Exception {}
                private void handleError(Exception e) {}
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(4, result.getValue()); // 3 methods, each with complexity 1, plus 1 for catch
    }

    @Test
    void testMethodWithMultipleCatchBlocks() throws IOException {
        // Method with multiple catch blocks - complexity increases for each catch
        String sourceCode = """
            public class TestClass {
                public void methodWithMultipleCatch() {
                    try {
                        riskyOperation();
                    } catch (IOException e) {
                        handleIOError(e);
                    } catch (SQLException e) {
                        handleSQLError(e);
                    } catch (Exception e) {
                        handleGenericError(e);
                    }
                }
                
                private void riskyOperation() throws Exception {}
                private void handleIOError(Exception e) {}
                private void handleSQLError(Exception e) {}
                private void handleGenericError(Exception e) {}
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(8, result.getValue()); // 5 methods + 3 catch blocks
    }

    @Test
    void testMethodWithTernaryOperator() throws IOException {
        // Method with ternary operator - complexity should be 2 (1 + 1 for ?)
        String sourceCode = """
            public class TestClass {
                public String methodWithTernary(boolean condition) {
                    return condition ? "true" : "false";
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getValue());
    }

    @Test
    void testMethodWithLogicalOperators() throws IOException {
        // Method with logical operators - complexity increases for && and ||
        String sourceCode = """
            public class TestClass {
                public boolean methodWithLogicalOps(boolean a, boolean b, boolean c) {
                    return a && b || c;
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(3, result.getValue()); // 1 + 1 (&&) + 1 (||)
    }

    @Test
    void testComplexMethod() throws IOException {
        // Complex method with multiple decision points
        String sourceCode = """
            public class TestClass {
                public String complexMethod(int value, boolean flag) {
                    if (value > 0) {
                        if (flag) {
                            for (int i = 0; i < value; i++) {
                                if (i % 2 == 0) {
                                    try {
                                        processEven(i);
                                    } catch (Exception e) {
                                        return "error";
                                    }
                                }
                            }
                            return flag && value > 10 ? "complex" : "simple";
                        }
                    }
                    return "default";
                }
                
                private void processEven(int i) throws Exception {}
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        // Expected complexity:
        // complexMethod: 1 + 1(if value>0) + 1(if flag) + 1(for) + 1(if i%2) + 1(catch) + 1(&&) + 1(?) = 8
        // processEven: 1
        // Total: 9
        assertEquals(9, result.getValue());
    }

    @Test
    void testMultipleMethods() throws IOException {
        // Class with multiple methods - complexity is sum of all methods
        String sourceCode = """
            public class TestClass {
                public void method1() {
                    // complexity 1
                }
                
                public void method2(boolean condition) {
                    if (condition) {
                        // complexity 2
                    }
                }
                
                public void method3(int value) {
                    switch (value) {
                        case 1:
                            break;
                        case 2:
                            break;
                        default:
                            break;
                    }
                    // complexity 4 (1 + 1 switch + 1 case1 + 1 case2 + 1 default)
                }
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(7, result.getValue()); // 1 + 2 + 4
    }

    @Test
    void testEmptyClass() throws IOException {
        // Empty class - complexity should be 0
        String sourceCode = """
            public class EmptyClass {
                // No methods
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getValue());
    }

    @Test
    void testClassWithOnlyFields() throws IOException {
        // Class with only fields, no methods - complexity should be 0
        String sourceCode = """
            public class DataClass {
                private String name;
                private int value;
                public static final String CONSTANT = "test";
            }
            """;

        StubClazz clazz = setupStubCall(sourceCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getValue());
    }

    @Test
    void testSupportsClass() {
        // Test with source code available
        StubClazz clazz = new StubClazz("TestClass", "com.test", Clazz.ClassType.SOURCE_ONLY, 
                new StubResourceLocation("/test/TestClass.java"), null);
        assertTrue(inspector.supports(clazz));

        // Test without source code
        StubClazz clazz2 = new StubClazz("TestClass", "com.test", Clazz.ClassType.BINARY_ONLY, null, null);
        assertFalse(inspector.supports(clazz2));
    }

    @Test
    void testNotApplicableWhenNoSourceLocation() {
        // Given - class without source location
        StubClazz clazz = new StubClazz("TestClass", "com.test", Clazz.ClassType.SOURCE_ONLY, null, null);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isNotApplicable());
        assertEquals("cyclomatic-complexity", result.getInspectorName());
    }

    @Test
    void testErrorWhenFileNotExists() {
        // Given
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubClazz clazz = new StubClazz("TestClass", "com.test", Clazz.ClassType.SOURCE_ONLY, sourceLocation, null);
        
        // Don't set file to exist in stub resolver (default is false)

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    void testErrorWhenIOException() throws IOException {
        // Given
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubClazz clazz = new StubClazz("TestClass", "com.test", Clazz.ClassType.SOURCE_ONLY, sourceLocation, null);
        
        // Set file to exist first, then set up IOException when trying to read
        stubResourceResolver.setFileExists(sourceLocation, true);
        stubResourceResolver.setIOException(sourceLocation, new IOException("File read error"));

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().contains("JavaParser error") || result.getErrorMessage().contains("File read error"));
    }

    @Test
    void testErrorWhenInvalidJavaCode() throws IOException {
        // Invalid Java syntax should result in parse error
        String invalidCode = """
            public class Invalid {
                public void method( {
                    // Missing closing parenthesis in method signature
                }
            }
            """;

        StubClazz clazz = setupStubCall(invalidCode);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().contains("Parse errors"));
    }

    private StubClazz setupStubCall(String sourceCode) throws IOException {
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubClazz clazz = new StubClazz("TestClass", "com.test", Clazz.ClassType.SOURCE_ONLY, sourceLocation, null);
        
        stubResourceResolver.setFileExists(sourceLocation, true);
        stubResourceResolver.setFileContent(sourceLocation, sourceCode);
        
        return clazz;
    }
}
