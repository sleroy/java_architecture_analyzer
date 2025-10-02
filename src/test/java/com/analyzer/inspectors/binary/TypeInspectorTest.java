package com.analyzer.inspectors.binary;

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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TypeInspector.
 * Tests the detection of Java class types from bytecode using ASM.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TypeInspector Unit Tests")
class TypeInspectorTest {

    @Mock
    private ResourceResolver mockResourceResolver;

    @Mock
    private Clazz mockClazz;

    @Mock
    private ResourceLocation mockBinaryLocation;

    private TypeInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new TypeInspector(mockResourceResolver);
    }

    @Test
    @DisplayName("Should have correct inspector metadata")
    void shouldHaveCorrectInspectorMetadata() {
        assertEquals("type", inspector.getName());
        assertEquals("class_type", inspector.getColumnName());
        assertTrue(inspector.getDescription().contains("type of declaration"));
    }

    @Test
    @DisplayName("Should support classes with binary location")
    void shouldSupportClassesWithBinaryLocation() {
        // Given
        when(mockClazz.getBinaryLocation()).thenReturn(mockBinaryLocation);

        // When
        boolean supports = inspector.supports(mockClazz);

        // Then
        assertTrue(supports);
    }

    @Test
    @DisplayName("Should not support classes without binary location")
    void shouldNotSupportClassesWithoutBinaryLocation() {
        // Given
        when(mockClazz.getBinaryLocation()).thenReturn(null);
        when(mockClazz.getClassType()).thenReturn(null);

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
        // Given - TypeInspector overrides supports() method, so we only need these
        // stubs
        when(mockClazz.getBinaryLocation()).thenReturn(null);
        when(mockClazz.getClassType()).thenReturn(null);

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isNotApplicable());
        assertEquals("type", result.getInspectorName());
    }

    @Test
    @DisplayName("Should detect regular class type")
    void shouldDetectRegularClassType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC, Object.class.getName());
        setupMockForAnalysis(classBytes);
        when(mockClazz.getClassName()).thenReturn("TestClass");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("type", result.getInspectorName());
        assertEquals("CLASS", result.getValue());
    }

    @Test
    @DisplayName("Should detect interface type")
    void shouldDetectInterfaceType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                Object.class.getName());
        setupMockForAnalysis(classBytes);
        when(mockClazz.getClassName()).thenReturn("TestInterface");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("type", result.getInspectorName());
        assertEquals("INTERFACE", result.getValue());
    }

    @Test
    @DisplayName("Should detect enum type")
    void shouldDetectEnumType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ENUM | Opcodes.ACC_SUPER,
                Enum.class.getName());
        setupMockForAnalysis(classBytes);
        when(mockClazz.getClassName()).thenReturn("TestEnum");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("type", result.getInspectorName());
        assertEquals("ENUM", result.getValue());
    }

    @Test
    @DisplayName("Should detect annotation type")
    void shouldDetectAnnotationType() throws IOException {
        // Given
        byte[] classBytes = createClassBytecode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION,
                Object.class.getName());
        setupMockForAnalysis(classBytes);
        when(mockClazz.getClassName()).thenReturn("TestAnnotation");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("type", result.getInspectorName());
        assertEquals("ANNOTATION", result.getValue());
    }

    @Test
    @DisplayName("Should detect record type")
    void shouldDetectRecordType() throws IOException {
        // Given - Note: ACC_RECORD was added in ASM 9.2 for Java 14+ records
        byte[] classBytes = createClassBytecode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, Object.class.getName());
        // Manually set the record flag if available
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD, "TestRecord", null,
                "java/lang/Record", null);
        cw.visitEnd();
        classBytes = cw.toByteArray();

        setupMockForAnalysis(classBytes);
        when(mockClazz.getClassName()).thenReturn("TestRecord");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("type", result.getInspectorName());
        assertEquals("RECORD", result.getValue());
    }

    @Test
    @DisplayName("Should handle IOException during analysis")
    void shouldHandleIOExceptionDuringAnalysis() throws IOException {
        // Given - TypeInspector overrides supports(), so we don't need hasBinaryCode()
        // stub
        when(mockClazz.getBinaryLocation()).thenReturn(mockBinaryLocation);
        when(mockResourceResolver.openStream(mockBinaryLocation))
                .thenThrow(new IOException("Network error"));

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isError());
        assertEquals("type", result.getInspectorName());
        assertTrue(result.getErrorMessage().contains("Error analyzing binary class"));
        assertTrue(result.getErrorMessage().contains("Network error"));
    }

    @Test
    @DisplayName("Should handle null input stream")
    void shouldHandleNullInputStream() throws IOException {
        // Given - TypeInspector overrides supports(), so we don't need hasBinaryCode()
        // stub
        when(mockClazz.getBinaryLocation()).thenReturn(mockBinaryLocation);
        when(mockResourceResolver.openStream(mockBinaryLocation)).thenReturn(null);
        when(mockBinaryLocation.getUri()).thenReturn(URI.create("test://class"));

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isError());
        assertEquals("type", result.getInspectorName());
        assertTrue(result.getErrorMessage().contains("Could not open binary class"));
    }

    @Test
    @DisplayName("Should handle invalid class file")
    void shouldHandleInvalidClassFile() throws IOException {
        // Given
        byte[] invalidBytes = "not a class file".getBytes();
        setupMockForAnalysis(invalidBytes);
        when(mockClazz.getClassName()).thenReturn("InvalidClass");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isError());
        assertEquals("type", result.getInspectorName());
        assertTrue(result.getErrorMessage().contains("Invalid class format") ||
                result.getErrorMessage().contains("Analysis error"));
    }

    @Test
    @DisplayName("Should handle empty class file")
    void shouldHandleEmptyClassFile() throws IOException {
        // Given
        byte[] emptyBytes = new byte[0];
        setupMockForAnalysis(emptyBytes);
        when(mockClazz.getClassName()).thenReturn("EmptyClass");

        // When
        InspectorResult result = inspector.decorate(mockClazz);

        // Then
        assertTrue(result.isError());
        assertEquals("type", result.getInspectorName());
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
     * Sets up mock objects for class analysis.
     */
    private void setupMockForAnalysis(byte[] classBytes) throws IOException {
        // TypeInspector overrides supports() method, so we don't need hasBinaryCode()
        // stub
        when(mockClazz.getBinaryLocation()).thenReturn(mockBinaryLocation);
        when(mockResourceResolver.openStream(mockBinaryLocation))
                .thenReturn(new ByteArrayInputStream(classBytes));
    }
}
