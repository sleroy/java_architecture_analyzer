package com.analyzer.inspectors.binary;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.rules.binary.TypeInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubProjectFile;
import com.analyzer.test.stubs.StubResourceLocation;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TypeInspector.
 * Tests the detection of Java class types from bytecode using ASM.
 */
@DisplayName("TypeInspector Unit Tests")
class TypeInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private TypeInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        inspector = new TypeInspector(stubResourceResolver);
    }

    @Test
    @DisplayName("Should have correct inspector metadata")
    void shouldHaveCorrectInspectorMetadata() {

        assertEquals("class_type", inspector.getColumnName());
    }

    @Test
    @DisplayName("Should support classes with binary location")
    void shouldSupportClassesWithBinaryLocation() {
        // Given
        ResourceLocation binaryLocation = new StubResourceLocation("/test/TestClass.class");
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY, null,
                binaryLocation);

        // When
        boolean supports = inspector.supports(clazz);

        // Then
        assertTrue(supports);
    }

    @Test
    @DisplayName("Should support classes with source-only type (has class type information)")
    void shouldSupportClassesWithSourceOnlyType() {
        // Given - class with SOURCE_ONLY type (TypeInspector supports classes with
        // class type info)
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY, null,
                null);

        // When
        boolean supports = inspector.supports(clazz);

        // Then
        assertTrue(supports); // TypeInspector supports classes that have class type information
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
    @DisplayName("Should analyze source-only classes using class type information")
    void shouldAnalyzeSourceOnlyClasses() {
        // Given - class with SOURCE_ONLY type (TypeInspector can work with existing
        // type info)
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY, null,
                null);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        // Since there's no binary location, it should return not applicable from
        // BinaryClassInspector.decorate()
        assertTrue(result.isNotApplicable());
    }

    @Test
    @DisplayName("Should detect regular class type")
    void shouldDetectRegularClassType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC, Object.class.getName());
        StubProjectFile clazz = setupClassForAnalysis("TestClass", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());

        assertEquals("CLASS", result.getValue());
    }

    @Test
    @DisplayName("Should detect interface type")
    void shouldDetectInterfaceType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                Object.class.getName());
        StubProjectFile clazz = setupClassForAnalysis("TestInterface", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());

        assertEquals("INTERFACE", result.getValue());
    }

    @Test
    @DisplayName("Should detect enum type")
    void shouldDetectEnumType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ENUM | Opcodes.ACC_SUPER,
                Enum.class.getName());
        StubProjectFile clazz = setupClassForAnalysis("TestEnum", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());

        assertEquals("ENUM", result.getValue());
    }

    @Test
    @DisplayName("Should detect annotation type")
    void shouldDetectAnnotationType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION,
                Object.class.getName());
        StubProjectFile clazz = setupClassForAnalysis("TestAnnotation", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());

        assertEquals("ANNOTATION", result.getValue());
    }

    @Test
    @DisplayName("Should detect record type")
    void shouldDetectRecordType() throws IOException {
        // Given - Note: ACC_RECORD was added in ASM 9.2 for Java 14+ records
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD, "TestRecord", null,
                "java/lang/Record", null);
        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        StubProjectFile clazz = setupClassForAnalysis("TestRecord", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());

        assertEquals("RECORD", result.getValue());
    }

    @Test
    @DisplayName("Should handle IOException during analysis")
    void shouldHandleIOExceptionDuringAnalysis() throws IOException {
        // Given
        ResourceLocation binaryLocation = new StubResourceLocation("/test/TestClass.class");
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY, null,
                binaryLocation);

        // Set up resource resolver to throw IOException
        stubResourceResolver.setIOException(binaryLocation, new IOException("Network error"));

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());

        assertTrue(result.getErrorMessage().contains("Error analyzing binary class"));

    }

    @Test
    @DisplayName("Should handle null input stream")
    void shouldHandleNullInputStream() throws IOException {
        // Given
        ResourceLocation binaryLocation = new StubResourceLocation("test://class");
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY, null,
                binaryLocation);

        // Don't set any content for the resource resolver, so it will throw IOException

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());

        assertTrue(result.getErrorMessage().contains("Error analyzing binary class") ||
                result.getErrorMessage().contains("Could not open binary class"));
    }

    @Test
    @DisplayName("Should handle invalid class file")
    void shouldHandleInvalidClassFile() throws IOException {
        // Given
        byte[] invalidBytes = "not a class file".getBytes();
        StubProjectFile clazz = setupClassForAnalysis("InvalidClass", invalidBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        // Invalid class files should result in an error or be handled gracefully
        // Check if it's an error or if the result indicates a problem
        assertTrue(result.isError() || result.getValue() == null ||
                (result.getValue() instanceof String && ((String) result.getValue()).isEmpty()),
                "Expected error result or empty value for invalid class file, but got: " + result);

    }

    @Test
    @DisplayName("Should handle empty class file")
    void shouldHandleEmptyClassFile() throws IOException {
        // Given
        byte[] emptyBytes = new byte[0];
        StubProjectFile clazz = setupClassForAnalysis("EmptyClass", emptyBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());

    }

    // Helper methods

    /**
     * Creates bytecode for a simple class with the given access flags.
     */
    private byte[] createClassBytecode(int accessFlags, String superClass) {
        ClassWriter cw = new ClassWriter(0);
        String superName = superClass.replace('.', '/');
        cw.visit(Opcodes.V17, accessFlags, "TestClass", null, superName, null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Sets up a StubProjectFile for class analysis with the given bytecode.
     */
    private StubProjectFile setupClassForAnalysis(String className, byte[] classBytes) throws IOException {
        StubProjectFile clazz = new StubProjectFile(className, "com.test", ClassType.BINARY_ONLY, null,
                null);

        // Create ResourceLocation from the actual file path that BinaryClassInspector
        // will use
        ResourceLocation binaryLocation = new ResourceLocation(clazz.getFilePath().toUri());

        // Set up the resource resolver to return the class bytes as an InputStream
        stubResourceResolver.setBinaryContent(binaryLocation, classBytes);

        return clazz;
    }
}
