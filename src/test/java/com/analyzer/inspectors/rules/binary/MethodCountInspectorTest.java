package com.analyzer.inspectors.rules.binary;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.test.stubs.StubProjectFile;
import com.analyzer.test.stubs.StubResourceLocation;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MethodCountInspector using binary analysis.
 * Tests the counting of methods in Java class bytecode using ASM.
 */
@DisplayName("MethodCountInspector Binary Unit Tests")
class MethodCountInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private MethodCountInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        inspector = new MethodCountInspector(stubResourceResolver);
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
    @DisplayName("Should not support null classes")
    void shouldNotSupportNullClasses() {
        // When
        boolean supports = inspector.supports(null);

        // Then
        assertFalse(supports);
    }

    @Test
    @DisplayName("Should count simple methods")
    void shouldCountSimpleMethods() throws IOException {
        // Given
        byte[] classBytes = createClassWithMethods("TestClass", 3);
        StubProjectFile clazz = setupClassForAnalysis("TestClass", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("method_count", result.getTagName());
        assertEquals(3, result.getValue());
    }

    @Test
    @DisplayName("Should count constructor as method")
    void shouldCountConstructorAsMethod() throws IOException {
        // Given
        byte[] classBytes = createClassWithConstructorAndMethods("TestClass", 2);
        StubProjectFile clazz = setupClassForAnalysis("TestClass", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("method_count", result.getTagName());
        assertEquals(3, result.getValue()); // 1 constructor + 2 methods
    }

    @Test
    @DisplayName("Should count static methods")
    void shouldCountStaticMethods() throws IOException {
        // Given
        byte[] classBytes = createClassWithStaticMethods("TestClass", 2, 1);
        StubProjectFile clazz = setupClassForAnalysis("TestClass", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("method_count", result.getTagName());
        assertEquals(3, result.getValue()); // 2 instance + 1 static method
    }

    @Test
    @DisplayName("Should count zero methods in empty class")
    void shouldCountZeroMethodsInEmptyClass() throws IOException {
        // Given
        byte[] classBytes = createEmptyClass("EmptyClass");
        StubProjectFile clazz = setupClassForAnalysis("EmptyClass", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("method_count", result.getTagName());
        assertEquals(0, result.getValue());
    }

    @Test
    @DisplayName("Should count methods with different access modifiers")
    void shouldCountMethodsWithDifferentAccessModifiers() throws IOException {
        // Given
        byte[] classBytes = createClassWithVariousAccessModifiers("TestClass");
        StubProjectFile clazz = setupClassForAnalysis("TestClass", classBytes);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isSuccessful());
        assertEquals("method_count", result.getTagName());
        assertEquals(4, result.getValue()); // public, private, protected, package-private
    }

    @Test
    @DisplayName("Should handle IOException during analysis")
    void shouldHandleIOExceptionDuringAnalysis() throws IOException {
        // Given
        ResourceLocation binaryLocation = new StubResourceLocation("/test/TestClass.class");
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.BINARY_ONLY, null,
                binaryLocation);

        // BinaryClassInspector creates ResourceLocation from the ProjectFile's path
        // So we need to set up the exception under that path too
        ResourceLocation actualLocation = new ResourceLocation(clazz.getFilePath().toUri());
        stubResourceResolver.setIOException(actualLocation, new IOException("Network error"));

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());
        assertEquals("method_count", result.getTagName());
        assertTrue(result.getErrorMessage().contains("Error analyzing binary class"));
        assertTrue(result.getErrorMessage().contains("Network error"));
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
        assertTrue(result.isError());
        assertEquals("method_count", result.getTagName());
    }

    @Test
    @DisplayName("Should return not applicable when class is not supported")
    void shouldReturnNotApplicableWhenClassIsNotSupported() {
        // Given - class with SOURCE_ONLY type and no binary location
        StubProjectFile clazz = new StubProjectFile("TestClass", "com.test", ClassType.SOURCE_ONLY, null,
                null);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isNotApplicable());
        assertEquals("method_count", result.getTagName());
    }

    // Helper methods

    /**
     * Creates bytecode for a class with the specified number of methods.
     */
    private byte[] createClassWithMethods(String className, int methodCount) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Add methods
        for (int i = 0; i < methodCount; i++) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "method" + i, "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Creates bytecode for a class with a constructor and specified number of
     * methods.
     */
    private byte[] createClassWithConstructorAndMethods(String className, int methodCount) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Add constructor
        MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        // Add methods
        for (int i = 0; i < methodCount; i++) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "method" + i, "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Creates bytecode for a class with both static and instance methods.
     */
    private byte[] createClassWithStaticMethods(String className, int instanceMethods, int staticMethods) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Add instance methods
        for (int i = 0; i < instanceMethods; i++) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "instanceMethod" + i, "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }

        // Add static methods
        for (int i = 0; i < staticMethods; i++) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "staticMethod" + i, "()V", null,
                    null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Creates bytecode for an empty class (no methods).
     */
    private byte[] createEmptyClass(String className) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Creates bytecode for a class with methods having different access modifiers.
     */
    private byte[] createClassWithVariousAccessModifiers(String className) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Public method
        MethodVisitor mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC, "publicMethod", "()V", null, null);
        mv1.visitCode();
        mv1.visitInsn(Opcodes.RETURN);
        mv1.visitMaxs(0, 1);
        mv1.visitEnd();

        // Private method
        MethodVisitor mv2 = cw.visitMethod(Opcodes.ACC_PRIVATE, "privateMethod", "()V", null, null);
        mv2.visitCode();
        mv2.visitInsn(Opcodes.RETURN);
        mv2.visitMaxs(0, 1);
        mv2.visitEnd();

        // Protected method
        MethodVisitor mv3 = cw.visitMethod(Opcodes.ACC_PROTECTED, "protectedMethod", "()V", null, null);
        mv3.visitCode();
        mv3.visitInsn(Opcodes.RETURN);
        mv3.visitMaxs(0, 1);
        mv3.visitEnd();

        // Package-private method (no access modifier)
        MethodVisitor mv4 = cw.visitMethod(0, "packagePrivateMethod", "()V", null, null);
        mv4.visitCode();
        mv4.visitInsn(Opcodes.RETURN);
        mv4.visitMaxs(0, 1);
        mv4.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Sets up a StubProjectFile for class analysis with the given bytecode.
     */
    private StubProjectFile setupClassForAnalysis(String className, byte[] classBytes) throws IOException {
        ResourceLocation binaryLocation = new StubResourceLocation("/test/" + className + ".class");
        StubProjectFile clazz = new StubProjectFile(className, "com.test", ClassType.BINARY_ONLY, null,
                binaryLocation);

        // BinaryClassInspector creates ResourceLocation from the ProjectFile's path
        // So we need to register the binary content under that path too
        ResourceLocation actualLocation = new ResourceLocation(clazz.getFilePath().toUri());
        stubResourceResolver.setBinaryContent(actualLocation, classBytes);

        return clazz;
    }
}
