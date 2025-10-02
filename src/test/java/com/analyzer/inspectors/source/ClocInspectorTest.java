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
 * Unit tests for ClocInspector (Lines of Code Inspector).
 * Tests CLOC-specific line counting functionality.
 * Base SourceFileInspector functionality is tested in SourceFileInspectorTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClocInspector Unit Tests")
class ClocInspectorTest {

    @Mock
    private ResourceResolver mockResourceResolver;

    @Mock
    private Clazz mockClazz;

    @Mock
    private ResourceLocation mockSourceLocation;

    private ClocInspector clocInspector;

    @BeforeEach
    void setUp() {
        clocInspector = new ClocInspector(mockResourceResolver);
    }

    @Test
    @DisplayName("Should return correct inspector metadata")
    void shouldReturnCorrectInspectorMetadata() {
        assertEquals("cloc", clocInspector.getName());
        assertEquals("lines_of_code", clocInspector.getColumnName());
        assertEquals("Counts the number of lines of code in source files", clocInspector.getDescription());
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

        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("cloc", result.getInspectorName());
        assertEquals(13L, result.getValue()); // 13 lines including empty lines
    }

    @Test
    @DisplayName("Should count lines correctly for single line file")
    void shouldCountLinesCorrectlyForSingleLineFile() throws IOException {
        // Given
        String javaCode = "public class SingleLine {}";
        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(1L, result.getValue());
    }

    @Test
    @DisplayName("Should count zero lines for empty file")
    void shouldCountZeroLinesForEmptyFile() throws IOException {
        // Given
        String javaCode = "";
        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(0L, result.getValue());
    }

    @Test
    @DisplayName("Should handle different line endings correctly")
    void shouldHandleDifferentLineEndingsCorrectly() throws IOException {
        // Given - File with mixed line endings (Windows CRLF, Unix LF)
        String javaCode = "line1\r\nline2\nline3\r\nline4";
        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(4L, result.getValue());
    }

    @Test
    @DisplayName("Should handle files with only whitespace lines")
    void shouldHandleFilesWithOnlyWhitespaceLines() throws IOException {
        // Given
        String javaCode = "\n  \n\t\n   \n";
        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

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

        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("cloc", result.getInspectorName());
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

        setupMockForSuccessfulAnalysis(largeContent.toString());

        // When
        long startTime = System.currentTimeMillis();
        InspectorResult result = clocInspector.decorate(mockClazz);
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

        setupMockForSuccessfulAnalysis(javaCode);

        // When
        InspectorResult result = clocInspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals(35L, result.getValue()); // Count all lines including comments and blank lines
    }

    /**
     * Helper method to set up mocks for successful analysis scenarios.
     */
    private void setupMockForSuccessfulAnalysis(String content) throws IOException {
        when(mockClazz.hasSourceCode()).thenReturn(true);
        when(mockClazz.getSourceLocation()).thenReturn(mockSourceLocation);
        when(mockResourceResolver.exists(mockSourceLocation)).thenReturn(true);

        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(mockResourceResolver.openStream(mockSourceLocation)).thenReturn(inputStream);
    }
}
