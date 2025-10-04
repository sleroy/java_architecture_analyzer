package com.analyzer.inspectors.source;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.rules.source.ClocInspector;
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
 * Unit tests for ClocInspector (Lines of Code Inspector).
 * Tests CLOC-specific line counting functionality.
 * Base SourceFileInspector functionality is tested in SourceFileInspectorTest.
 */
@DisplayName("ClocInspector Unit Tests")
class ClocInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private ClocInspector clocInspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        clocInspector = new ClocInspector(stubResourceResolver);
    }

    @Test
    @DisplayName("Should return correct inspector metadata")
    void shouldReturnCorrectInspectorMetadata() {
        assertEquals("cloc", clocInspector.getColumnName());
    }

    @Test
    @DisplayName("Should count lines correctly for simple Java file")
    void shouldCountLinesCorrectlyForSimpleJavaFile() throws IOException {
        // Given
        String javaCode = "package com.example;\n\n" +
                "public class TestClass {\n" +
                "    private String name;\n\n" +
                "    public TestClass(String name) {\n" +
                "        this.name = name;\n" +
                "    }\n\n" +
                "    public String getName() {\n" +
                "        return name;\n" +
                "    }\n" +
                "}";

        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("cloc", result.getTagName());
        assertEquals(13L, result.getValue()); // 13 lines including empty lines
    }

    @Test
    @DisplayName("Should count lines correctly for single line file")
    void shouldCountLinesCorrectlyForSingleLineFile() throws IOException {
        // Given
        String javaCode = "public class SingleLine {}";
        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(1L, result.getValue());
    }

    @Test
    @DisplayName("Should count zero lines for empty file")
    void shouldCountZeroLinesForEmptyFile() throws IOException {
        // Given
        String javaCode = "";
        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(0L, result.getValue());
    }

    @Test
    @DisplayName("Should handle different line endings correctly")
    void shouldHandleDifferentLineEndingsCorrectly() throws IOException {
        // Given - File with mixed line endings (Windows CRLF, Unix LF)
        String javaCode = "line1\r\nline2\nline3\r\nline4";
        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        System.out.println(result.getErrorMessage());
        assertTrue(result.isSuccessful());
        assertEquals(4L, result.getValue());
    }

    @Test
    @DisplayName("Should handle files with only whitespace lines")
    void shouldHandleFilesWithOnlyWhitespaceLines() throws IOException {
        // Given
        String javaCode = "\n  \n\t\n   \n";
        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(4L, result.getValue()); // Each line counts, even if whitespace-only
    }

    @Test
    @DisplayName("Should handle Unicode content correctly")
    void shouldHandleUnicodeContentCorrectly() throws IOException {
        // Given - Java file with Unicode characters and comments
        String javaCode = "// 这是一个中文注释\n" +
                "public class UnicodeTest {\n" +
                "    // العربية comment\n" +
                "    private String message = \"Hello 世界! 🌍\";\n\n" +
                "    public void método() {\n" +
                "        System.out.println(\"Ελληνικά: \" + message);\n" +
                "    }\n" +
                "}";

        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("cloc", result.getTagName());
        assertEquals(9L, result.getValue());
    }

    @Test
    @DisplayName("Should handle very large files efficiently")
    void shouldHandleVeryLargeFilesEfficiently() throws IOException {
        // Given - Create a large file with many lines
        StringBuilder largeContent = new StringBuilder();
        int lineCount = 10000;
        for (int i = 0; i < lineCount; i++) {
            largeContent.append("// Line ").append(i + 1).append("\n");
        }

        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(largeContent.toString());

        // When
        long startTime = System.currentTimeMillis();
        InspectorResult result = clocInspector.decorate(projectFile);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertTrue(result.isSuccessful());
        assertEquals((long) lineCount, result.getValue());
        assertTrue(duration < 1000, "Should process large files quickly (took " + duration + "ms)");
    }

    @Test
    @DisplayName("Should handle files with various Java constructs")
    void shouldHandleFilesWithVariousJavaConstructs() throws IOException {
        // Given - Complex Java file with various constructs
        String javaCode = "package com.analyzer.test;\n\n" +
                "import java.util.List;\n" +
                "import java.util.ArrayList;\n\n" +
                "/**\n" +
                " * Javadoc comment\n" +
                " * Multiple lines\n" +
                " */\n" +
                "@SuppressWarnings(\"unchecked\")\n" +
                "public class ComplexClass<T> implements Comparable<T> {\n\n" +
                "    /* Block comment */\n" +
                "    private static final String CONSTANT = \"value\";\n\n" +
                "    // Single line comment\n" +
                "    private List<T> items = new ArrayList<>();\n\n" +
                "    public ComplexClass() {\n" +
                "        // Constructor\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public int compareTo(T other) {\n" +
                "        return 0; // Placeholder\n" +
                "    }\n\n" +
                "    public void method() {\n" +
                "        if (items.isEmpty()) {\n" +
                "            System.out.println(\"Empty\");\n" +
                "        } else {\n" +
                "            items.forEach(System.out::println);\n" +
                "        }\n" +
                "    }\n" +
                "}";

        StubProjectFile projectFile = setupStubForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(35L, result.getValue()); // Count all lines including comments and blank lines
    }

    @Test
    @DisplayName("Should handle file not found error")
    void shouldHandleFileNotFoundError() {
        // Given
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);
        projectFile.setSourceLocationUri(sourceLocation.toString());

        // Don't set file to exist in stub resolver (default is false)

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isError());
        assertEquals("cloc", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Source file not found"));
    }

    @Test
    @DisplayName("Should handle IOException during analysis")
    void shouldHandleIOExceptionDuringAnalysis() throws IOException {
        // Given
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        // So we need to set up the exception under that path
        ResourceLocation actualLocation = new ResourceLocation(projectFile.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setIOException(actualLocation, new IOException("File read error"));

        // When
        InspectorResult result = clocInspector.decorate(projectFile);

        // Then
        assertTrue(result.isError());
        assertEquals("cloc", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Error counting lines"));
    }

    /**
     * Helper method to set up stubs for successful analysis scenarios.
     */
    private StubProjectFile setupStubForSuccessfulAnalysis(String content) throws IOException {
        ResourceLocation sourceLocation = new StubResourceLocation("/test/TestClass.java");
        StubProjectFile projectFile = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY);
        projectFile.setHasSourceCode(true);
        projectFile.setSourceLocationUri(sourceLocation.toString());

        // SourceFileInspector creates ResourceLocation from the ProjectFile's path
        // So we need to set up the content under that path too
        ResourceLocation actualLocation = new ResourceLocation(projectFile.getFilePath().toUri());
        stubResourceResolver.setFileExists(actualLocation, true);
        stubResourceResolver.setFileContent(actualLocation, content);

        return projectFile;
    }
}
