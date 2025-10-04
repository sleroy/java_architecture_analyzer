package com.analyzer.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for verifying the typed getter methods in ProjectFile
 */
public class ProjectFileTypedGettersTest {

    private ProjectFile projectFile;
    private Path testFilePath;
    private Path projectRoot;

    @BeforeEach
    void setUp() {
        projectRoot = Paths.get("/test/project");
        testFilePath = Paths.get("/test/project/src/main/java/Example.java");
        projectFile = new ProjectFile(testFilePath, projectRoot);
    }

    @Test
    void testStringTag() {
        // Test null value
        assertNull(projectFile.getStringTag("nonexistent"));

        // Test with default
        assertEquals("default", projectFile.getStringTag("nonexistent", "default"));

        // Test actual string value
        projectFile.setTag("stringValue", "hello world");
        assertEquals("hello world", projectFile.getStringTag("stringValue"));

        // Test non-string value converted to string
        projectFile.setTag("numberAsString", 42);
        assertEquals("42", projectFile.getStringTag("numberAsString"));
    }

    @Test
    void testIntegerTag() {
        // Test null value
        assertNull(projectFile.getIntegerTag("nonexistent"));

        // Test with default
        assertEquals(Integer.valueOf(100), projectFile.getIntegerTag("nonexistent", 100));

        // Test actual Integer value
        projectFile.setTag("intValue", 42);
        assertEquals(Integer.valueOf(42), projectFile.getIntegerTag("intValue"));

        // Test string number converted to Integer
        projectFile.setTag("stringNumber", "123");
        assertEquals(Integer.valueOf(123), projectFile.getIntegerTag("stringNumber"));

        // Test Long converted to Integer
        projectFile.setTag("longValue", 999L);
        assertEquals(Integer.valueOf(999), projectFile.getIntegerTag("longValue"));

        // Test invalid conversion
        projectFile.setTag("invalidNumber", "not a number");
        assertNull(projectFile.getIntegerTag("invalidNumber"));
    }

    @Test
    void testLongTag() {
        // Test null value
        assertNull(projectFile.getLongTag("nonexistent"));

        // Test with default
        assertEquals(Long.valueOf(200L), projectFile.getLongTag("nonexistent", 200L));

        // Test actual Long value
        projectFile.setTag("longValue", 123456789L);
        assertEquals(Long.valueOf(123456789L), projectFile.getLongTag("longValue"));

        // Test string number converted to Long
        projectFile.setTag("stringLong", "987654321");
        assertEquals(Long.valueOf(987654321L), projectFile.getLongTag("stringLong"));

        // Test Integer converted to Long
        projectFile.setTag("intValue", 42);
        assertEquals(Long.valueOf(42L), projectFile.getLongTag("intValue"));
    }

    @Test
    void testDoubleTag() {
        // Test null value
        assertNull(projectFile.getDoubleTag("nonexistent"));

        // Test with default
        assertEquals(Double.valueOf(3.14), projectFile.getDoubleTag("nonexistent", 3.14));

        // Test actual Double value
        projectFile.setTag("doubleValue", 2.718);
        assertEquals(Double.valueOf(2.718), projectFile.getDoubleTag("doubleValue"));

        // Test string number converted to Double
        projectFile.setTag("stringDouble", "1.414");
        assertEquals(Double.valueOf(1.414), projectFile.getDoubleTag("stringDouble"));

        // Test Integer converted to Double
        projectFile.setTag("intValue", 5);
        assertEquals(Double.valueOf(5.0), projectFile.getDoubleTag("intValue"));
    }

    @Test
    void testBooleanTag() {
        // Test null value
        assertNull(projectFile.getBooleanTag("nonexistent"));

        // Test with default
        assertEquals(Boolean.TRUE, projectFile.getBooleanTag("nonexistent", true));

        // Test actual Boolean value
        projectFile.setTag("boolValue", true);
        assertEquals(Boolean.TRUE, projectFile.getBooleanTag("boolValue"));

        // Test string "true" converted to Boolean
        projectFile.setTag("stringTrue", "true");
        assertEquals(Boolean.TRUE, projectFile.getBooleanTag("stringTrue"));

        // Test string "false" converted to Boolean
        projectFile.setTag("stringFalse", "false");
        assertEquals(Boolean.FALSE, projectFile.getBooleanTag("stringFalse"));

        // Test string "1" converted to Boolean
        projectFile.setTag("stringOne", "1");
        assertEquals(Boolean.TRUE, projectFile.getBooleanTag("stringOne"));

        // Test string "0" converted to Boolean
        projectFile.setTag("stringZero", "0");
        assertEquals(Boolean.FALSE, projectFile.getBooleanTag("stringZero"));

        // Test invalid conversion
        projectFile.setTag("invalidBoolean", "maybe");
        assertNull(projectFile.getBooleanTag("invalidBoolean"));
    }

    @Test
    void testListTag() {
        // Test null value
        assertNull(projectFile.getListTag("nonexistent"));

        // Test with default
        List<String> defaultList = Arrays.asList("default1", "default2");
        assertEquals(defaultList, projectFile.getListTag("nonexistent", defaultList));

        // Test actual List value
        List<String> testList = Arrays.asList("item1", "item2", "item3");
        projectFile.setTag("listValue", testList);
        assertEquals(testList, projectFile.getListTag("listValue"));

        // Test non-List value (should return null)
        projectFile.setTag("notAList", "just a string");
        assertNull(projectFile.getListTag("notAList"));
    }

    @Test
    void testMixedTypesInRealScenario() {
        // Simulate real usage scenario with Java file tags
        projectFile.setTag("java.className", "Example");
        projectFile.setTag("java.packageName", "com.example");
        projectFile.setTag("java.lineCount", 150);
        projectFile.setTag("java.hasMainMethod", true);
        projectFile.setTag("java.complexity", 12.5);
        projectFile.setTag("java.imports", Arrays.asList("java.util.List", "java.io.File"));

        // Test retrieval with typed getters
        assertEquals("Example", projectFile.getStringTag("java.className"));
        assertEquals("com.example", projectFile.getStringTag("java.packageName"));
        assertEquals(Integer.valueOf(150), projectFile.getIntegerTag("java.lineCount"));
        assertEquals(Boolean.TRUE, projectFile.getBooleanTag("java.hasMainMethod"));
        assertEquals(Double.valueOf(12.5), projectFile.getDoubleTag("java.complexity"));

        List<String> imports = projectFile.getListTag("java.imports");
        assertNotNull(imports);
        assertEquals(2, imports.size());
        assertTrue(imports.contains("java.util.List"));
        assertTrue(imports.contains("java.io.File"));
    }
}
