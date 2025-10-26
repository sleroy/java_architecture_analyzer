package com.analyzer.rules.metrics;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for ThreadLocalUsageInspector.
 * Verifies that ThreadLocal fields are correctly detected and marked in class
 * nodes.
 */
@ExtendWith(MockitoExtension.class)
class ThreadLocalUsageInspectorTest {

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private JARClassLoaderService classLoaderService;

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private ProjectFileDecorator projectFileDecorator;

    private ThreadLocalUsageInspector inspector;
    private URLClassLoader testClassLoader;

    @BeforeEach
    void setUp() throws Exception {
        // Create a URLClassLoader that includes the test-classes directory
        URL testClassesUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        testClassLoader = new URLClassLoader(new URL[] { testClassesUrl }, getClass().getClassLoader());

        // Mock the classLoaderService to return our test ClassLoader (lenient for tests that don't use it)
        lenient().when(classLoaderService.getSharedClassLoader()).thenReturn(testClassLoader);

        inspector = new ThreadLocalUsageInspector(resourceResolver, classLoaderService, graphRepository);
    }

    @Test
    void testDetectsThreadLocalFields() throws Exception {
        // Given: A class with ThreadLocal fields
        ProjectFile projectFile = createProjectFile("test_samples.threadlocal.ThreadLocalExample");
        JavaClassNode classNode = new JavaClassNode("test_samples.threadlocal.ThreadLocalExample");
        when(graphRepository.getNodeById(anyString())).thenReturn(Optional.of(classNode));


        // When: The inspector analyzes the class
        inspector.inspect(projectFile, projectFileDecorator);

        // Then: ThreadLocal usage should be detected
        assertTrue(classNode.hasTag(ThreadLocalUsageInspector.TAG_USES_THREADLOCAL));
        assertEquals(6, (int) classNode.getProperty(ThreadLocalUsageInspector.PROP_THREADLOCAL_COUNT));
    }

    @Test
    void testDetectsInheritableThreadLocal() throws Exception {
        // Given: A class with InheritableThreadLocal (subclass of ThreadLocal)
        ProjectFile projectFile = createProjectFile("test_samples.threadlocal.ThreadLocalExample");
        JavaClassNode classNode = new JavaClassNode("test_samples.threadlocal.ThreadLocalExample");
        when(graphRepository.getNodeById(anyString())).thenReturn(Optional.of(classNode));

        // When: The inspector analyzes the class
        inspector.inspect(projectFile, projectFileDecorator);

        // Then: InheritableThreadLocal should be counted as ThreadLocal usage
        assertEquals(6, (int) classNode.getProperty(ThreadLocalUsageInspector.PROP_THREADLOCAL_COUNT));
    }

    @Test
    void testCapturesFieldNames() throws Exception {
        // Given: A class with multiple ThreadLocal fields
        ProjectFile projectFile = createProjectFile("test_samples.threadlocal.ThreadLocalExample");
        JavaClassNode classNode = new JavaClassNode("test_samples.threadlocal.ThreadLocalExample");
        when(graphRepository.getNodeById(anyString())).thenReturn(Optional.of(classNode));

        // When: The inspector analyzes the class
        inspector.inspect(projectFile, projectFileDecorator);

        // Then: Field names should be captured
        Object fields = classNode.getProperty(ThreadLocalUsageInspector.PROP_THREADLOCAL_FIELDS);
        assertNotNull(fields);
        assertTrue(fields.toString().contains("userContext"));
        assertTrue(fields.toString().contains("dateFormatter"));
        assertTrue(fields.toString().contains("requestId"));
    }

    @Test
    void testNoThreadLocalDetected() {
        // Given: A class without ThreadLocal fields
        ProjectFile projectFile = createProjectFile("test_samples.cache_singleton.UserRegistry");
        JavaClassNode classNode = new JavaClassNode("test_samples.cache_singleton.UserRegistry");
        when(graphRepository.getNodeById(anyString())).thenReturn(Optional.of(classNode));

        // When: The inspector analyzes the class
        inspector.inspect(projectFile, projectFileDecorator);

        // Then: ThreadLocal should not be detected
        assertFalse(classNode.hasTag(ThreadLocalUsageInspector.TAG_USES_THREADLOCAL));
        assertNull(classNode.getProperty(ThreadLocalUsageInspector.PROP_THREADLOCAL_COUNT));
    }

    @Test
    void testInspectorName() {
        // Then: Inspector should have correct name
        assertEquals("ThreadLocalUsageInspector", inspector.getName());
    }

    @Test
    void testSupportsProjectFile() {
        // Given: A valid ProjectFile
        ProjectFile projectFile = createProjectFile("test_samples.threadlocal.ThreadLocalExample");

        // When/Then: Inspector should support the file
        assertTrue(inspector.supports(projectFile));
    }

    @Test
    void testHandlesClassNotFound() {
        // Given: A non-existent class
        ProjectFile projectFile = createProjectFile("com.nonexistent.FakeClass");

        // When: The inspector analyzes the class
        inspector.inspect(projectFile, projectFileDecorator);

        // Then: Should handle gracefully without throwing exception
        verify(projectFileDecorator).error(any(ClassNotFoundException.class));
    }

    /**
     * Helper method to create a ProjectFile for testing.
     */
    private ProjectFile createProjectFile(String fullyQualifiedName) {
        ProjectFile projectFile = mock(ProjectFile.class);
        lenient().when(projectFile.getProperty("fullyQualifiedName")).thenReturn(fullyQualifiedName);
        return projectFile;
    }
}
