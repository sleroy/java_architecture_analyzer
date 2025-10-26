package com.analyzer.core.export;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PropertyNestingTransformer.
 */
class PropertyNestingTransformerTest {

    @Test
    void testNestProperties_EmptyMap() {
        Map<String, Object> flat = new HashMap<>();
        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testNestProperties_NullMap() {
        Map<String, Object> result = PropertyNestingTransformer.nestProperties(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testNestProperties_FlatKeys() {
        Map<String, Object> flat = new HashMap<>();
        flat.put("name", "TestClass");
        flat.put("version", 1);
        flat.put("enabled", true);

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        assertEquals(3, result.size());
        assertEquals("TestClass", result.get("name"));
        assertEquals(1, result.get("version"));
        assertEquals(true, result.get("enabled"));
    }

    @Test
    void testNestProperties_SingleLevelNesting() {
        Map<String, Object> flat = new HashMap<>();
        flat.put("file.type", "java");
        flat.put("file.size", 1024);
        flat.put("file.encoding", "UTF-8");

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("file"));

        @SuppressWarnings("unchecked")
        Map<String, Object> fileProps = (Map<String, Object>) result.get("file");
        assertEquals(3, fileProps.size());
        assertEquals("java", fileProps.get("type"));
        assertEquals(1024, fileProps.get("size"));
        assertEquals("UTF-8", fileProps.get("encoding"));
    }

    @Test
    void testNestProperties_MultiLevelNesting() {
        Map<String, Object> flat = new HashMap<>();
        flat.put("ejb.metadata.type", "stateless");
        flat.put("ejb.metadata.version", "3.0");
        flat.put("ejb.config.poolSize", 10);

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("ejb"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ejbProps = (Map<String, Object>) result.get("ejb");
        assertEquals(2, ejbProps.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> metadataProps = (Map<String, Object>) ejbProps.get("metadata");
        assertEquals("stateless", metadataProps.get("type"));
        assertEquals("3.0", metadataProps.get("version"));

        @SuppressWarnings("unchecked")
        Map<String, Object> configProps = (Map<String, Object>) ejbProps.get("config");
        assertEquals(10, configProps.get("poolSize"));
    }

    @Test
    void testNestProperties_MixedKeys() {
        Map<String, Object> flat = new HashMap<>();
        flat.put("name", "TestClass");
        flat.put("file.type", "java");
        flat.put("file.size", 1024);
        flat.put("complexity", 5);
        flat.put("ejb.type", "stateless");

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        assertEquals(4, result.size());
        assertEquals("TestClass", result.get("name"));
        assertEquals(5, result.get("complexity"));

        @SuppressWarnings("unchecked")
        Map<String, Object> fileProps = (Map<String, Object>) result.get("file");
        assertEquals("java", fileProps.get("type"));
        assertEquals(1024, fileProps.get("size"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ejbProps = (Map<String, Object>) result.get("ejb");
        assertEquals("stateless", ejbProps.get("type"));
    }

    @Test
    void testNestProperties_DeepNesting() {
        Map<String, Object> flat = new HashMap<>();
        flat.put("a.b.c.d.e", "deep");

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) result.get("a");
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) a.get("b");
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) b.get("c");
        @SuppressWarnings("unchecked")
        Map<String, Object> d = (Map<String, Object>) c.get("d");

        assertEquals("deep", d.get("e"));
    }

    @Test
    void testNestProperties_NullValue() {
        Map<String, Object> flat = new HashMap<>();
        flat.put("file.type", "java");
        flat.put("file.metadata", null);

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        @SuppressWarnings("unchecked")
        Map<String, Object> fileProps = (Map<String, Object>) result.get("file");
        assertEquals("java", fileProps.get("type"));
        assertNull(fileProps.get("metadata"));
    }

    @Test
    void testFlattenProperties_EmptyMap() {
        Map<String, Object> nested = new HashMap<>();
        Map<String, Object> result = PropertyNestingTransformer.flattenProperties(nested);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFlattenProperties_FlatKeys() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("name", "TestClass");
        nested.put("version", 1);

        Map<String, Object> result = PropertyNestingTransformer.flattenProperties(nested);

        assertEquals(2, result.size());
        assertEquals("TestClass", result.get("name"));
        assertEquals(1, result.get("version"));
    }

    @Test
    void testFlattenProperties_SingleLevelNesting() {
        Map<String, Object> nested = new HashMap<>();
        Map<String, Object> fileProps = new HashMap<>();
        fileProps.put("type", "java");
        fileProps.put("size", 1024);
        nested.put("file", fileProps);

        Map<String, Object> result = PropertyNestingTransformer.flattenProperties(nested);

        assertEquals(2, result.size());
        assertEquals("java", result.get("file.type"));
        assertEquals(1024, result.get("file.size"));
    }

    @Test
    void testFlattenProperties_MultiLevelNesting() {
        Map<String, Object> nested = new HashMap<>();
        Map<String, Object> ejbProps = new HashMap<>();
        Map<String, Object> metadataProps = new HashMap<>();
        metadataProps.put("type", "stateless");
        metadataProps.put("version", "3.0");
        ejbProps.put("metadata", metadataProps);
        nested.put("ejb", ejbProps);

        Map<String, Object> result = PropertyNestingTransformer.flattenProperties(nested);

        assertEquals(2, result.size());
        assertEquals("stateless", result.get("ejb.metadata.type"));
        assertEquals("3.0", result.get("ejb.metadata.version"));
    }

    @Test
    void testRoundTrip_NestThenFlatten() {
        Map<String, Object> original = new HashMap<>();
        original.put("file.type", "java");
        original.put("file.size", 1024);
        original.put("ejb.type", "stateless");
        original.put("complexity", 5);

        Map<String, Object> nested = PropertyNestingTransformer.nestProperties(original);
        Map<String, Object> flattened = PropertyNestingTransformer.flattenProperties(nested);

        assertEquals(original.size(), flattened.size());
        assertEquals(original.get("file.type"), flattened.get("file.type"));
        assertEquals(original.get("file.size"), flattened.get("file.size"));
        assertEquals(original.get("ejb.type"), flattened.get("ejb.type"));
        assertEquals(original.get("complexity"), flattened.get("complexity"));
    }

    @Test
    void testRoundTrip_FlattenThenNest() {
        Map<String, Object> original = new HashMap<>();
        Map<String, Object> fileProps = new HashMap<>();
        fileProps.put("type", "java");
        fileProps.put("size", 1024);
        original.put("file", fileProps);
        original.put("complexity", 5);

        Map<String, Object> flattened = PropertyNestingTransformer.flattenProperties(original);
        Map<String, Object> nested = PropertyNestingTransformer.nestProperties(flattened);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultFileProps = (Map<String, Object>) nested.get("file");

        assertEquals(original.size(), nested.size());
        assertEquals(fileProps.get("type"), resultFileProps.get("type"));
        assertEquals(fileProps.get("size"), resultFileProps.get("size"));
        assertEquals(original.get("complexity"), nested.get("complexity"));
    }

    @Test
    void testNestProperties_RealWorldExample() {
        // Example of properties that might be set by inspectors
        Map<String, Object> flat = new HashMap<>();
        flat.put("java.className", "HelloWorldBean");
        flat.put("java.packageName", "com.example.ejb");
        flat.put("java.classType", "class");
        flat.put("ejb.type", "stateless");
        flat.put("ejb.version", "3.0");
        flat.put("ejb.transactionType", "CONTAINER");
        flat.put("metrics.complexity", 12);
        flat.put("metrics.lines", 145);
        flat.put("spring.conversionTarget", "SERVICE");

        Map<String, Object> result = PropertyNestingTransformer.nestProperties(flat);

        // Verify structure
        assertEquals(4, result.size());
        assertTrue(result.containsKey("java"));
        assertTrue(result.containsKey("ejb"));
        assertTrue(result.containsKey("metrics"));
        assertTrue(result.containsKey("spring"));

        @SuppressWarnings("unchecked")
        Map<String, Object> javaProps = (Map<String, Object>) result.get("java");
        assertEquals("HelloWorldBean", javaProps.get("className"));
        assertEquals("com.example.ejb", javaProps.get("packageName"));
        assertEquals("class", javaProps.get("classType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ejbProps = (Map<String, Object>) result.get("ejb");
        assertEquals("stateless", ejbProps.get("type"));
        assertEquals("3.0", ejbProps.get("version"));
        assertEquals("CONTAINER", ejbProps.get("transactionType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metricsProps = (Map<String, Object>) result.get("metrics");
        assertEquals(12, metricsProps.get("complexity"));
        assertEquals(145, metricsProps.get("lines"));

        @SuppressWarnings("unchecked")
        Map<String, Object> springProps = (Map<String, Object>) result.get("spring");
        assertEquals("SERVICE", springProps.get("conversionTarget"));
    }
}
