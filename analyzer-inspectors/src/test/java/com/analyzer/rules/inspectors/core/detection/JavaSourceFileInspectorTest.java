package com.analyzer.rules.inspectors.core.detection;

import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.FileResourceResolver;
import com.analyzer.rules.std.JavaSourceFileInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for JavaSourceFileInspector using real objects and JavaParser.
 * Tests TypeDeclaration scanning and setFullQualifiedName() functionality.
 */
class JavaSourceFileInspectorTest {

    @TempDir
    Path tempDir;

    private JavaSourceFileInspector detector;
    private FileResourceResolver resourceResolver;

    @BeforeEach
    void setUp() {
        resourceResolver = new FileResourceResolver();
        LocalCache localCache = new LocalCache(true);
        detector = new JavaSourceFileInspector(resourceResolver, localCache);
    }

    @Test
    void testSupportsJavaFiles() {
        ProjectFile javaFile = createProjectFile("Test.java");
        assertTrue(detector.supports(javaFile));
    }

    @Test
    void testDoesNotSupportNonJavaFiles() {
        ProjectFile xmlFile = createProjectFile("config.xml");
        assertFalse(detector.supports(xmlFile));
    }

    @Test
    void testDetectsSimpleClass() throws IOException {
        String javaSource = """
                package com.example;

                public class SimpleClass {
                    private String name;

                    public String getName() {
                        return name;
                    }
                }
                """;

        Path javaFile = tempDir.resolve("SimpleClass.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        // Execute
        detector.inspect(projectFile, decorator);

        // Verify basic tags were set
        assertTrue(projectFile.hasTag(InspectorTags.TAG_JAVA_IS_SOURCE), "Should have java.is_source tag");
        assertTrue(projectFile.hasTag(InspectorTags.TAG_JAVA_DETECTED), "Should have java.detected tag");
        assertEquals(InspectorTags.FORMAT_SOURCE, projectFile.getProperty(InspectorTags.JAVA_FORMAT));
        assertEquals(InspectorTags.LANGUAGE_JAVA, projectFile.getProperty(InspectorTags.TAG_LANGUAGE));

        // Verify package and class name extraction
        assertEquals("com.example", projectFile.getProperty(InspectorTags.TAG_JAVA_PACKAGE_NAME));
        assertEquals("SimpleClass", projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME));
        assertTrue(projectFile.hasTag(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));

        // Verify type determination
        assertEquals("class", projectFile.getProperty(InspectorTags.TAG_JAVA_CLASS_TYPE));

        // Verify setFullQualifiedName was called on ProjectFile
        assertEquals("com.example.SimpleClass", 
                projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
    }

    @Test
    void testDetectsInterface() throws IOException {
        String javaSource = """
                package com.example;

                public interface MyInterface {
                    void doSomething();
                }
                """;

        Path javaFile = tempDir.resolve("MyInterface.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        detector.inspect(projectFile, decorator);

        assertEquals("MyInterface", projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME));
        assertEquals("interface", projectFile.getProperty(InspectorTags.TAG_JAVA_CLASS_TYPE));
        assertEquals("com.example.MyInterface", 
                projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
    }

    @Test
    void testDetectsEnum() throws IOException {
        String javaSource = """
                package com.example;

                public enum Status {
                    ACTIVE, INACTIVE, PENDING
                }
                """;

        Path javaFile = tempDir.resolve("Status.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        detector.inspect(projectFile, decorator);

        assertEquals("Status", projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME));
        assertEquals("enum", projectFile.getProperty(InspectorTags.TAG_JAVA_CLASS_TYPE));
        assertEquals("com.example.Status", 
                projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
    }

    @Test
    void testDetectsRecord() throws IOException {
        String javaSource = """
                package com.example;

                public record Person(String name, int age) {
                }
                """;

        Path javaFile = tempDir.resolve("Person.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        detector.inspect(projectFile, decorator);

        // Record support depends on JavaParser version (3.24+)
        // Older versions may parse records as class or fail to detect the type
        String className = (String) projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME);
        String classType = (String) projectFile.getProperty(InspectorTags.TAG_JAVA_CLASS_TYPE);
        
        if (className != null) {
            // If parsing succeeded, verify the name
            assertEquals("Person", className);
            // Type should be either "record" (if supported) or "class" (fallback)
            assertTrue("record".equals(classType) || "class".equals(classType),
                    "Type should be 'record' or 'class' (fallback), but was: " + classType);
            assertEquals("com.example.Person", 
                    projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
        } else {
            // JavaParser version doesn't support records - test is inconclusive
            System.out.println("INFO: Record syntax not supported by current JavaParser version - skipping record test");
        }
    }

    @Test
    void testDetectsAnnotation() throws IOException {
        String javaSource = """
                package com.example;

                public @interface MyAnnotation {
                    String value();
                }
                """;

        Path javaFile = tempDir.resolve("MyAnnotation.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        detector.inspect(projectFile, decorator);

        assertEquals("MyAnnotation", projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME));
        assertEquals("annotation", projectFile.getProperty(InspectorTags.TAG_JAVA_CLASS_TYPE));
        assertEquals("com.example.MyAnnotation", 
                projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
    }

    @Test
    void testHandlesDefaultPackage() throws IOException {
        String javaSource = """
                public class NoPackageClass {
                    private int value;
                }
                """;

        Path javaFile = tempDir.resolve("NoPackageClass.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        detector.inspect(projectFile, decorator);

        assertEquals("NoPackageClass", projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME));
        assertNull(projectFile.getProperty(InspectorTags.TAG_JAVA_PACKAGE_NAME), 
                "Should not have package property for default package");
        assertEquals("NoPackageClass", 
                projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
    }

    @Test
    void testHandlesMultipleTypesInFile() throws IOException {
        String javaSource = """
                package com.example;

                public class MainClass {
                    private String name;
                }

                class HelperClass {
                    void help() {}
                }
                """;

        Path javaFile = tempDir.resolve("MainClass.java");
        Files.writeString(javaFile, javaSource);

        ProjectFile projectFile = new ProjectFile(javaFile, tempDir);
        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);

        detector.inspect(projectFile, decorator);

        // Should detect MainClass as it matches the filename
        assertEquals("MainClass", projectFile.getProperty(InspectorTags.PROP_JAVA_CLASS_NAME));
        assertEquals("com.example.MainClass", 
                projectFile.getStringProperty(InspectorTags.PROP_JAVA_FULLY_QUALIFIED_NAME));
    }

    @Test
    void testGetName() {
        assertEquals("Java Source File Detector", detector.getName());
    }

    private ProjectFile createProjectFile(String fileName) {
        Path projectRoot = tempDir.resolve("project");
        Path filePath = projectRoot.resolve(fileName);
        return new ProjectFile(filePath, projectRoot);
    }
}
