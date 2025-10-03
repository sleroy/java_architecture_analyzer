package com.analyzer.inspectors.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.source.RegExpFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegExpFileInspectorTest {

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Clazz clazz;

    @Mock
    private ResourceLocation sourceLocation;

    private TestRegExpFileInspector inspector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        inspector = new TestRegExpFileInspector(resourceResolver, "@\\w+");
    }

    @Test
    void testConstructorWithStringPattern() {
        TestRegExpFileInspector inspector = new TestRegExpFileInspector(resourceResolver, "@\\w+");
        assertNotNull(inspector.getPattern());
        assertEquals("@\\w+", inspector.getPattern().pattern());
    }

    @Test
    void testConstructorWithCompiledPattern() {
        Pattern pattern = Pattern.compile("@\\w+");
        TestRegExpFileInspector inspector = new TestRegExpFileInspector(resourceResolver, pattern);
        assertNotNull(inspector.getPattern());
        assertEquals("@\\w+", inspector.getPattern().pattern());
    }

    @Test
    void testConstructorWithNullStringPattern() {
        assertThrows(IllegalArgumentException.class, () -> 
            new TestRegExpFileInspector(resourceResolver, (String) null));
    }

    @Test
    void testConstructorWithEmptyStringPattern() {
        assertThrows(IllegalArgumentException.class, () -> 
            new TestRegExpFileInspector(resourceResolver, ""));
    }

    @Test
    void testConstructorWithNullCompiledPattern() {
        assertThrows(IllegalArgumentException.class, () -> 
            new TestRegExpFileInspector(resourceResolver, (Pattern) null));
    }

    @Test
    void testSupportsClass() {
        when(clazz.hasSourceCode()).thenReturn(true);
        assertTrue(inspector.supports(clazz));

        when(clazz.hasSourceCode()).thenReturn(false);
        assertFalse(inspector.supports(clazz));
    }

    @Test
    void testDecorateWithMatchingPattern() throws IOException {
        // Setup
        String sourceCode = "@Override\npublic void test() {}";
        when(clazz.hasSourceCode()).thenReturn(true);
        when(clazz.getSourceLocation()).thenReturn(sourceLocation);
        when(resourceResolver.exists(sourceLocation)).thenReturn(true);
        when(resourceResolver.openStream(sourceLocation))
            .thenReturn(new ByteArrayInputStream(sourceCode.getBytes()));

        // Execute
        InspectorResult result = inspector.decorate(clazz);

        // Verify
        assertTrue(result.isSuccessful());
        assertEquals(Boolean.TRUE, result.getValue());
        assertEquals("test-regexp", result.getInspectorName());
    }

    @Test
    void testDecorateWithNonMatchingPattern() throws IOException {
        // Setup
        String sourceCode = "public void test() {}";
        when(clazz.hasSourceCode()).thenReturn(true);
        when(clazz.getSourceLocation()).thenReturn(sourceLocation);
        when(resourceResolver.exists(sourceLocation)).thenReturn(true);
        when(resourceResolver.openStream(sourceLocation))
            .thenReturn(new ByteArrayInputStream(sourceCode.getBytes()));

        // Execute
        InspectorResult result = inspector.decorate(clazz);

        // Verify
        assertTrue(result.isSuccessful());
        assertEquals(Boolean.FALSE, result.getValue());
        assertEquals("test-regexp", result.getInspectorName());
    }

    @Test
    void testDecorateWithNullSourceLocation() {
        when(clazz.hasSourceCode()).thenReturn(true);
        when(clazz.getSourceLocation()).thenReturn(null);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isNotApplicable());
        assertEquals("test-regexp", result.getInspectorName());
    }

    @Test
    void testDecorateWithNonExistentSourceFile() {
        when(clazz.hasSourceCode()).thenReturn(true);
        when(clazz.getSourceLocation()).thenReturn(sourceLocation);
        when(resourceResolver.exists(sourceLocation)).thenReturn(false);

        InspectorResult result = inspector.decorate(clazz);

        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    void testDecorateWithIOException() throws IOException {
        when(clazz.hasSourceCode()).thenReturn(true);
        when(clazz.getSourceLocation()).thenReturn(sourceLocation);
        when(resourceResolver.exists(sourceLocation)).thenReturn(true);
        when(resourceResolver.openStream(sourceLocation))
            .thenThrow(new IOException("File read error"));

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

        public TestRegExpFileInspector(ResourceResolver resourceResolver, String regexPattern) {
            super(resourceResolver, regexPattern);
        }

        public TestRegExpFileInspector(ResourceResolver resourceResolver, Pattern pattern) {
            super(resourceResolver, pattern);
        }

        @Override
        public String getName() {
            return "test-regexp";
        }

        @Override
        public String getColumnName() {
            return "Test RegExp";
        }

        @Override
        public String getDescription() {
            return "Test regular expression inspector";
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
