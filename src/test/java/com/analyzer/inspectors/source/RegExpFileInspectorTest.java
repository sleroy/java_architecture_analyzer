package com.analyzer.inspectors.source;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.source.RegExpFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubProjectFile;
import com.analyzer.test.stubs.StubResourceLocation;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RegExpFileInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private TestRegExpFileInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        inspector = new TestRegExpFileInspector(stubResourceResolver, "@\\w+");
    }

    @Test
    void testConstructorWithStringPattern() {
        TestRegExpFileInspector inspector = new TestRegExpFileInspector(stubResourceResolver, "@\\w+");
        assertNotNull(inspector.getPattern());
        assertEquals("@\\w+", inspector.getPattern().pattern());
    }

    @Test
    void testConstructorWithCompiledPattern() {
        Pattern pattern = Pattern.compile("@\\w+");
        TestRegExpFileInspector inspector = new TestRegExpFileInspector(stubResourceResolver, pattern);
        assertNotNull(inspector.getPattern());
        assertEquals("@\\w+", inspector.getPattern().pattern());
    }

    @Test
    void testConstructorWithNullStringPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestRegExpFileInspector(stubResourceResolver, (String) null));
    }

    @Test
    void testConstructorWithEmptyStringPattern() {
        assertThrows(IllegalArgumentException.class, () -> new TestRegExpFileInspector(stubResourceResolver, ""));
    }

    @Test
    void testConstructorWithNullCompiledPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new TestRegExpFileInspector(stubResourceResolver, (Pattern) null));
    }

    @Test
    void testSupportsClass() {
        // Test with source code available
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);
        assertTrue(inspector.supports(clazz));

        // Test without source code
        StubProjectFile clazz2 = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY);
        clazz2.setHasSourceCode(false);
        assertFalse(inspector.supports(clazz2));
    }

    @Test
    void testDecorateWithMatchingPattern() throws IOException {
        // Setup
        String sourceCode = "@Override\npublic void test() {}";
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        ResourceLocation actualLocation = new ResourceLocation(clazz.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setFileContent(actualLocation, sourceCode);

        // Execute
        InspectorResult result = inspector.decorate(clazz);

        // Verify
        assertTrue(result.isSuccessful());
        assertEquals(Boolean.TRUE, result.getValue());
        assertEquals("test-regexp", result.getTagName());
    }

    @Test
    void testDecorateWithNonMatchingPattern() throws IOException {
        // Setup
        String sourceCode = "public void test() {}";
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        ResourceLocation actualLocation = new ResourceLocation(clazz.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setFileContent(actualLocation, sourceCode);

        // Execute
        InspectorResult result = inspector.decorate(clazz);

        // Verify
        assertTrue(result.isSuccessful());
        assertEquals(Boolean.FALSE, result.getValue());
        assertEquals("test-regexp", result.getTagName());
    }

    @Test
    void testDecorateWithNullSourceLocation() {
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);
        // Don't set any file content in stub resolver, so it will generate a "file not
        // found" error

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isError());
        assertEquals("test-regexp", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    void testDecorateWithNonExistentSourceFile() {
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);
        clazz.setSourceLocationUri(sourceLocation.toString());

        // Don't set file to exist in stub resolver (default is false)

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    void testDecorateWithIOException() throws IOException {
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        clazz.setHasSourceCode(true);

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        ResourceLocation actualLocation = new ResourceLocation(clazz.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setIOException(actualLocation, new IOException("File read error"));

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().contains("Error reading source file"));
    }

    @Test
    void testMatchesWithNullContent() {
        assertFalse(inspector.matches(null));
    }

    @Test
    void testMatchesWithEmptyContent() {
        assertFalse(inspector.matches(""));
    }

    @Test
    void testMatchesWithMatchingContent() {
        assertTrue(inspector.matches("@Override"));
        assertTrue(inspector.matches("This has @Test annotation"));
    }

    @Test
    void testMatchesWithNonMatchingContent() {
        assertFalse(inspector.matches("public void test() {}"));
        assertFalse(inspector.matches("No annotations here"));
    }

    // Test implementation of RegExpFileInspector
    private static class TestRegExpFileInspector extends RegExpFileInspector {

        public TestRegExpFileInspector(StubResourceResolver resourceResolver, String regexPattern) {
            super(resourceResolver, regexPattern);
        }

        public TestRegExpFileInspector(StubResourceResolver resourceResolver, Pattern pattern) {
            super(resourceResolver, pattern);
        }

        @Override
        public String getColumnName() {
            return "test-regexp";
        }

        @Override
        public String getName() {
            return "Test RegExp";
        }

        // Expose protected method for testing
        public Pattern getPattern() {
            return super.getPattern();
        }

        // Expose protected method for testing
        public boolean matches(String content) {
            return super.matches(content);
        }
    }
}
