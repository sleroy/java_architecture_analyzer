package com.analyzer.inspectors.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SourceFileInspector base class functionality.
 * Tests common behavior for all source file inspectors.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SourceFileInspector Unit Tests")
class SourceFileInspectorTest {

    @Mock
    private ResourceResolver mockResourceResolver;

    @Mock
    private Clazz mockClazz;

    @Mock
    private ResourceLocation mockSourceLocation;

    private TestSourceFileInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new TestSourceFileInspector(mockResourceResolver);
    }

    @Test
    @DisplayName("Should support classes with source code")
    void shouldSupportClassesWithSourceCode() {
        // Given
        when(mockClazz.hasSourceCode()).thenReturn(true);

        // When
        boolean supports = inspector.supports(mockClazz);

        // Then
        assertTrue(supports);
    }

    @Test
    @DisplayName("Should not support classes without source code")
    void shouldNotSupportClassesWithoutSourceCode() {
        // Given
        when(mockClazz.hasSourceCode()).thenReturn(false);

        // When
        boolean supports = inspector.supports(mockClazz);

        // Then
        assertFalse(supports);
    }

    @Test
    @DisplayName("Should not support null classes")
    void shouldNotSupportNullClasses() {
        // When
        boolean supports = inspector.supports(null);

        // Then
        assertFalse(supports);
    }

    @Test
    @DisplayName("Should return not applicable when class is not supported")
    void shouldReturnNotApplicableWhenClassIsNotSupported() {
        // Given
        when(mockClazz.hasSourceCode()).thenReturn(false);

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isNotApplicable());
        assertEquals("test", result.getInspectorName());
    }

    @Test
    @DisplayName("Should return not applicable when class has no source location")
    void shouldReturnNotApplicableWhenClassHasNoSourceLocation() {
        // Given
        when(mockClazz.hasSourceCode()).thenReturn(true);
        when(mockClazz.getSourceLocation()).thenReturn(null);

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isNotApplicable());
        assertEquals("test", result.getInspectorName());
    }

    @Test
    @DisplayName("Should return error when source file does not exist")
    void shouldReturnErrorWhenSourceFileDoesNotExist() {
        // Given
        when(mockClazz.hasSourceCode()).thenReturn(true);
        when(mockClazz.getSourceLocation()).thenReturn(mockSourceLocation);
        when(mockResourceResolver.exists(mockSourceLocation)).thenReturn(false);

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isError());
        assertEquals("test", result.getInspectorName());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    @DisplayName("Should return error when IOException occurs during analysis")
    void shouldReturnErrorWhenIOExceptionOccursDuringAnalysis() throws IOException {
        // Given
        when(mockClazz.hasSourceCode()).thenReturn(true);
        when(mockClazz.getSourceLocation()).thenReturn(mockSourceLocation);
        when(mockResourceResolver.exists(mockSourceLocation)).thenReturn(true);
        when(mockResourceResolver.openStream(mockSourceLocation))
                .thenThrow(new IOException("Network error"));

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isError());
        assertEquals("test", result.getInspectorName());
        assertTrue(result.getErrorMessage().contains("Error analyzing source file"));
        assertTrue(result.getErrorMessage().contains("Network error"));
    }

    @Test
    @DisplayName("Should call analyzeSourceFile when preconditions are met")
    void shouldCallAnalyzeSourceFileWhenPreconditionsAreMet() throws IOException {
        // Given
        String content = "test content";
        when(mockClazz.hasSourceCode()).thenReturn(true);
        when(mockClazz.getSourceLocation()).thenReturn(mockSourceLocation);
        when(mockResourceResolver.exists(mockSourceLocation)).thenReturn(true);

        // Create a new InputStream each time the method is called
        when(mockResourceResolver.openStream(mockSourceLocation))
                .thenAnswer(invocation -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("test", result.getInspectorName());
        assertEquals("analyzed: " + content, result.getValue());
    }

    @Test
    @DisplayName("Should handle readFileContent correctly")
    void shouldHandleReadFileContentCorrectly() throws IOException {
        // Given
        String content = "Hello\nWorld\n";
        when(mockResourceResolver.openStream(mockSourceLocation))
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        // When
        String result = inspector.readFileContent(mockSourceLocation);

        // Then
        assertEquals(content, result);
    }

    @Test
    @DisplayName("Should handle countLines correctly")
    void shouldHandleCountLinesCorrectly() throws IOException {
        // Given
        String content = "line1\nline2\nline3\n";
        when(mockResourceResolver.openStream(mockSourceLocation))
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        // When
        long result = inspector.countLines(mockSourceLocation);

        // Then
        assertEquals(3L, result);
    }

    /**
     * Test implementation of SourceFileInspector for testing purposes.
     */
    private static class TestSourceFileInspector extends SourceFileInspector {

        public TestSourceFileInspector(ResourceResolver resourceResolver) {
            super(resourceResolver);
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public String getColumnName() {
            return "test_column";
        }

        @Override
        public String getDescription() {
            return "Test inspector";
        }

        @Override
        protected InspectorResult analyzeSourceFile(Clazz clazz, ResourceLocation sourceLocation) throws IOException {
            // Simple test implementation that reads content and returns it
            String content = readFileContent(sourceLocation);
            return new InspectorResult(getName(), (Object) ("analyzed: " + content));
        }
    }
}
