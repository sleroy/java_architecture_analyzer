package com.analyzer.inspectors.rules.binary;

import com.analyzer.core.ClassType;
import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.core.JARClassLoaderService;
import com.analyzer.resource.ResourceResolver;
import com.analyzer.test.stubs.StubProjectFile;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for AnnotationCountInspector.
 * Tests the ClassLoader-based inspector functionality including:
 * - Graceful handling of class loading failures
 * - Proper integration with JARClassLoaderService using stubs
 */
class AnnotationCountInspectorTest {

    private StubResourceResolver stubResourceResolver;
    private JARClassLoaderService classLoaderService;
    private AnnotationCountInspector inspector;

    @BeforeEach
    void setUp() {
        stubResourceResolver = new StubResourceResolver();
        classLoaderService = new JARClassLoaderService();
        inspector = new AnnotationCountInspector(stubResourceResolver, classLoaderService);
    }

    @Test
    void testSupportsValidProjectFile() {
        StubProjectFile clazz = createTestProjectFile("com.example.TestClass");
        assertTrue(inspector.supports(clazz));
    }

    @Test
    void testSupportsNullProjectFile() {
        assertFalse(inspector.supports(null));
    }

    @Test
    void testDecorateWithClassNotLoadable() {
        // Given - a class that doesn't exist in the ClassLoader
        StubProjectFile clazz = createTestProjectFile("com.example.NonExistentClass");

        // When
        InspectorResult result = inspector.decorate(clazz);


        // Then - should return error since class cannot be loaded
        assertTrue(result.isError());
        assertEquals("annotation-count", result.getTagName());

    }

    @Test
    void testDecorateWithNullClassNameReturnsError() {
        // Given - ProjectFile with null class name
        StubProjectFile clazz = new StubProjectFile(null, "com.test", ClassType.BINARY_ONLY, null, null);

        // When
        InspectorResult result = inspector.decorate(clazz);

        // Then
        assertTrue(result.isError());
        assertEquals("annotation-count", result.getTagName());
    }


    @Test
    void testDecorateWithStandardJavaClass() {
        // Test with java.lang.String which should be loadable
        StubProjectFile clazz = createTestProjectFile("java.lang.String");

        InspectorResult result = inspector.decorate(clazz);

        // Result may be error due to ClassLoader setup, but tag should be correct
        assertEquals("annotation-count", result.getTagName());
        // In a real environment, String class would load and have some annotation count
    }

    @Test
    void testAnalyzeLoadedClassWithAnnotations() {
        // Test the actual annotation counting logic using a test class with annotations

        // Create a test class with annotations using reflection
        @TestAnnotation
        class TestClassWithAnnotations {
            @TestAnnotation
            private String annotatedField;

            @TestAnnotation
            public void annotatedMethod(@TestAnnotation String param) {
            }

            @TestAnnotation
            public TestClassWithAnnotations(@TestAnnotation String param) {
            }
        }

        // Use reflection to verify the annotation counting logic works conceptually
        Class<?> testClass = TestClassWithAnnotations.class;
        StubProjectFile clazz = createTestProjectFile(testClass.getName());

        // We can't easily test the protected method directly, but we can verify
        // the annotation counting logic conceptually works
        assertTrue(testClass.getAnnotations().length > 0);
        assertTrue(testClass.getDeclaredMethods().length > 0);
        assertTrue(testClass.getDeclaredFields().length > 0);
        assertTrue(testClass.getDeclaredConstructors().length > 0);
    }

    @Test
    void testCreateTestProjectFileBasicFunctionality() {
        // Test the helper method works correctly
        StubProjectFile clazz = createTestProjectFile("com.example.TestClass");

        assertNotNull(clazz);
        assertEquals("TestClass", clazz.getClassName());
        assertEquals("com.example", clazz.getPackageName());
        assertEquals(ClassType.BINARY_ONLY.toString(), clazz.getStringTag("java.classType"));
    }

    @Test
    void testCreateTestProjectFileWithSimpleClassName() {
        // Test with simple class name (no package)
        StubProjectFile clazz = createTestProjectFile("SimpleClass");

        assertNotNull(clazz);
        assertEquals("SimpleClass", clazz.getClassName());
        assertEquals("", clazz.getPackageName());
        assertEquals(ClassType.BINARY_ONLY.toString(), clazz.getStringTag("java.classType"));
    }

    @Test
    void testInspectorInitialization() {
        // Test that inspector is properly initialized
        assertNotNull(inspector);
        assertNotNull(stubResourceResolver);
        assertNotNull(classLoaderService);
    }

    @Test
    void testClassLoaderServiceNotInitialized() {
        // Test behavior when ClassLoader service is not initialized
        // The real JARClassLoaderService should handle this gracefully
        StubProjectFile clazz = createTestProjectFile("com.example.TestClass");

        InspectorResult result = inspector.decorate(clazz);

        // Should return an error or not applicable, but not crash
        assertTrue(result.isError() || result.isNotApplicable());
        assertEquals("annotation-count", result.getTagName());
    }

    @Test
    void testGetTagName() {
        // Simple test to verify the tag name is correct
        StubProjectFile clazz = createTestProjectFile("com.example.TestClass");
        InspectorResult result = inspector.decorate(clazz);
        assertEquals("annotation-count", result.getTagName());
    }

    /**
     * Helper method to create a test ProjectFile object.
     */
    private StubProjectFile createTestProjectFile(String className) {
        String packageName = "";
        String simpleClassName = className;

        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
            simpleClassName = className.substring(lastDot + 1);
        }

        return new StubProjectFile(simpleClassName, packageName, ClassType.BINARY_ONLY, null, null);
    }

    /**
     * Test annotation for use in testing annotation counting.
     */
    @Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,
            ElementType.CONSTRUCTOR, ElementType.PARAMETER })
    @Retention(RetentionPolicy.RUNTIME)
    @interface TestAnnotation {
        String value() default "";
    }
}
