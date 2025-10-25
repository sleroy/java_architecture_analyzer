package com.analyzer.core;

import com.analyzer.core.model.ProjectFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for verifying the property and tag methods in ProjectFile.
 */
public class ProjectFilePropertiesAndTagsTest {

    private ProjectFile projectFile;
    private Path testFilePath;
    private Path projectRoot;

    @BeforeEach
    void setUp() {
        projectRoot = Paths.get("/test/project");
        testFilePath = Paths.get("/test/project/src/main/java/InventoryExample.java");
        projectFile = new ProjectFile(testFilePath, projectRoot);
    }

    @Test
    void testStringProperty() {
        // Test null value
        assertNull(projectFile.getProperty("nonexistent"));

        // Test actual string value
        projectFile.setProperty("stringValue", "hello world");
        assertEquals("hello world", projectFile.getProperty("stringValue"));
    }

    @Test
    void testIntegerProperty() {
        // Test null value
        assertNull(projectFile.getProperty("nonexistent"));

        // Test actual Integer value
        projectFile.setProperty("intValue", 42);
        assertEquals(42, projectFile.getProperty("intValue"));
    }

    @Test
    void testLongProperty() {
        // Test null value
        assertNull(projectFile.getProperty("nonexistent"));

        // Test actual Long value
        projectFile.setProperty("longValue", 123456789L);
        assertEquals(123456789L, projectFile.getProperty("longValue"));
    }

    @Test
    void testDoubleProperty() {
        // Test null value
        assertNull(projectFile.getProperty("nonexistent"));

        // Test actual Double value
        projectFile.setProperty("doubleValue", 2.718);
        assertEquals(2.718, projectFile.getProperty("doubleValue"));
    }

    @Test
    void testBooleanProperty() {
        // Test null value
        assertNull(projectFile.getProperty("nonexistent"));

        // Test actual Boolean value
        projectFile.setProperty("boolValue", true);
        assertEquals(true, projectFile.getProperty("boolValue"));
    }

    @Test
    void testListProperty() {
        // Test null value
        assertNull(projectFile.getProperty("nonexistent"));

        // Test actual List value
        List<String> testList = Arrays.asList("item1", "item2", "item3");
        projectFile.setProperty("listValue", testList);
        assertEquals(testList, projectFile.getProperty("listValue"));
    }

    @Test
    void testTags() {
        // Test hasTag on nonexistent tag
        assertFalse(projectFile.hasTag("nonexistent"));

        // Test addTag and hasTag
        projectFile.addTag("simple_tag");
        assertTrue(projectFile.hasTag("simple_tag"));

        // Test getTags
        assertEquals(1, projectFile.getTags().size());
        assertTrue(projectFile.getTags().contains("simple_tag"));
    }

    @Test
    void testMixedTypesInRealScenario() {
        // Simulate real usage scenario with Java file properties and tags
        projectFile.setProperty("java.className", "InventoryExample");
        projectFile.setProperty("java.packageName", "com.example");
        projectFile.setProperty("java.lineCount", 150);
        projectFile.addTag("java.hasMainMethod");
        projectFile.setProperty("java.complexity", 12.5);
        projectFile.setProperty("java.imports", Arrays.asList("java.util.List", "java.io.File"));

        // Test retrieval with getProperty and hasTag
        assertEquals("InventoryExample", projectFile.getProperty("java.className"));
        assertEquals("com.example", projectFile.getProperty("java.packageName"));
        assertEquals(150, projectFile.getProperty("java.lineCount"));
        assertTrue(projectFile.hasTag("java.hasMainMethod"));
        assertEquals(12.5, projectFile.getProperty("java.complexity"));

        @SuppressWarnings("unchecked")
        List<String> imports = (List<String>) projectFile.getProperty("java.imports");
        assertNotNull(imports);
        assertEquals(2, imports.size());
        assertTrue(imports.contains("java.util.List"));
        assertTrue(imports.contains("java.io.File"));
    }
}
