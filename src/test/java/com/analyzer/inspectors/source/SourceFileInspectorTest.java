package com.analyzer.inspectors.source;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.source.SourceFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubProjectFile;
import com.analyzer.test.stubs.StubResourceLocation;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SourceFileInspector base class functionality.
 * Tests common behavior for all source file inspectors.
 */
@DisplayName("SourceFileInspector Unit Tests")
class SourceFileInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private TestSourceFileInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        inspector = new TestSourceFileInspector(stubResourceResolver);
    }

    @Test
    @DisplayName("Should support classes with source code")
    void shouldSupportClassesWithSourceCode() {
        // Given
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);

        // When
        boolean supports = inspector.supports(projectFile);

        // Then
        assertTrue(supports);
    }

    @Test
    @DisplayName("Should not support classes without source code")
    void shouldNotSupportClassesWithoutSourceCode() {
        // Given
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY);
        projectFile.setHasSourceCode(false);

        // When
        boolean supports = inspector.supports(projectFile);

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
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY);
        projectFile.setHasSourceCode(false);

        // When
        InspectorResult result = inspector.decorate(projectFile);

        // Then
        assertTrue(result.isNotApplicable());
        assertEquals("test", result.getTagName());
    }

    @Test
    @DisplayName("Should return error when class has no source location")
    void shouldReturnErrorWhenClassHasNoSourceLocation() {
        // Given
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);
        // Don't set source location - will be null, but ProjectFile still generates a
        // file path

        // When
        InspectorResult result = inspector.decorate(projectFile);

        // Then
        assertTrue(result.isError());
        assertEquals("test", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    @DisplayName("Should return error when source file does not exist")
    void shouldReturnErrorWhenSourceFileDoesNotExist() {
        // Given
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);
        projectFile.setSourceLocationUri(sourceLocation.toString());

        // Don't set file to exist in stub resolver (default is false)

        // When
        InspectorResult result = inspector.decorate(projectFile);

        // Then
        assertTrue(result.isError());
        assertEquals("test", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    @DisplayName("Should return error when IOException occurs during analysis")
    void shouldReturnErrorWhenIOExceptionOccursDuringAnalysis() throws IOException {
        // Given
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);
        projectFile.setSourceLocationUri(sourceLocation.toString());

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        // So we need to set up the exception under that path too
        ResourceLocation actualLocation = new ResourceLocation(projectFile.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setIOException(actualLocation, new IOException("Network error"));

        // When
        InspectorResult result = inspector.decorate(projectFile);

        // Then
        assertTrue(result.isError());
        assertEquals("test", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Error analyzing source file"));
        assertTrue(result.getErrorMessage().contains("Network error"));
    }

    @Test
    @DisplayName("Should call analyzeSourceFile when preconditions are met")
    void shouldCallAnalyzeSourceFileWhenPreconditionsAreMet() throws IOException {
        // Given
        String content = "test content";
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);
        projectFile.setSourceLocationUri(sourceLocation.toString());

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        // So we need to set up the content under that path too
        ResourceLocation actualLocation = new ResourceLocation(projectFile.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setFileContent(actualLocation, content);

        // When
        InspectorResult result = inspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("test", result.getTagName());
        assertEquals("analyzed: " + content, result.getValue());
    }

    @Test
    @DisplayName("Should handle readFileContent correctly")
    void shouldHandleReadFileContentCorrectly() throws IOException {
        // Given
        String content = "Hello\nWorld\n";
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");

        stubResourceResolver.setFileContent(sourceLocation, content);

        // When
        String result = inspector.testReadFileContent(sourceLocation);

        // Then
        assertEquals(content, result);
    }

    @Test
    @DisplayName("Should handle countLines correctly")
    void shouldHandleCountLinesCorrectly() throws IOException {
        // Given
        String content = "line1\nline2\nline3\n";
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");

        stubResourceResolver.setFileContent(sourceLocation, content);

        // When
        long result = inspector.testCountLines(sourceLocation);

        // Then
        assertEquals(3L, result);
    }

    @Test
    @DisplayName("Should handle empty file content correctly")
    void shouldHandleEmptyFileContentCorrectly() throws IOException {
        // Given
        String content = "";
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");

        stubResourceResolver.setFileContent(sourceLocation, content);

        // When
        String result = inspector.testReadFileContent(sourceLocation);

        // Then
        assertEquals(content, result);
    }

    @Test
    @DisplayName("Should handle single line content correctly")
    void shouldHandleSingleLineContentCorrectly() throws IOException {
        // Given
        String content = "single line";
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");

        stubResourceResolver.setFileContent(sourceLocation, content);

        // When
        long result = inspector.testCountLines(sourceLocation);

        // Then
        assertEquals(1L, result);
    }

    /**
     * Test implementation of SourceFileInspector for testing purposes.
     */
    private static class TestSourceFileInspector extends SourceFileInspector {

        public TestSourceFileInspector(StubResourceResolver resourceResolver) {
            super(resourceResolver);
        }

        @Override
        public String getColumnName() {
            return "test";
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        protected InspectorResult analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation)
                throws IOException {
            // Simple test implementation that reads content and returns it
            String content = readFileContent(sourceLocation);
            return new InspectorResult(getColumnName(), (Object) ("analyzed: " + content));
        }

        // Public wrapper methods to expose protected methods for testing
        public String testReadFileContent(ResourceLocation location) throws IOException {
            return readFileContent(location);
        }

        public long testCountLines(ResourceLocation location) throws IOException {
            return countLines(location);
        }
    }
}
