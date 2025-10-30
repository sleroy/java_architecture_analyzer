package com.analyzer.core.serialization;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.model.ProjectFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonSerializationService.
 * Validates centralized JSON serialization for all graph nodes.
 */
class JsonSerializationServiceTest {

    private JsonSerializationService service;

    @BeforeEach
    void setUp() {
        service = new JsonSerializationService();
    }

    // ==================== PROPERTIES SERIALIZATION TESTS ====================

    @Test
    void testSerializeProperties_EmptyMap() {
        Map<String, Object> properties = new HashMap<>();
        String json = service.serializeProperties(properties);
        assertEquals("{}", json);
    }

    @Test
    void testSerializeProperties_NullMap() {
        String json = service.serializeProperties(null);
        assertEquals("{}", json);
    }

    @Test
    void testSerializeProperties_SimpleTypes() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("stringProp", "value");
        properties.put("intProp", 42);
        properties.put("doubleProp", 3.14);
        properties.put("boolProp", true);

        String json = service.serializeProperties(properties);

        assertNotNull(json);
        assertTrue(json.contains("\"stringProp\":\"value\""));
        assertTrue(json.contains("\"intProp\":42"));
        assertTrue(json.contains("\"doubleProp\":3.14"));
        assertTrue(json.contains("\"boolProp\":true"));
    }

    @Test
    void testSerializeProperties_NestedMaps() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("key1", "value1");

        Map<String, Object> properties = new HashMap<>();
        properties.put("nested", nested);
        properties.put("simple", "test");

        String json = service.serializeProperties(properties);

        assertNotNull(json);
        assertTrue(json.contains("\"nested\""));
        assertTrue(json.contains("\"key1\":\"value1\""));
    }

    @Test
    void testDeserializeProperties_EmptyJson() {
        Map<String, Object> result = service.deserializeProperties("{}");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testDeserializeProperties_NullJson() {
        Map<String, Object> result = service.deserializeProperties(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testDeserializeProperties_ValidJson() {
        String json = "{\"name\":\"test\",\"count\":42,\"active\":true}";
        Map<String, Object> result = service.deserializeProperties(json);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("test", result.get("name"));
        assertEquals(42, ((Number) result.get("count")).intValue());
        assertEquals(true, result.get("active"));
    }

    @Test
    void testSerializeDeserializeProperties_RoundTrip() {
        Map<String, Object> original = new HashMap<>();
        original.put("string", "value");
        original.put("number", 123);
        original.put("boolean", false);

        String json = service.serializeProperties(original);
        Map<String, Object> deserialized = service.deserializeProperties(json);

        assertNotNull(deserialized);
        assertEquals("value", deserialized.get("string"));
        assertEquals(123, ((Number) deserialized.get("number")).intValue());
        assertEquals(false, deserialized.get("boolean"));
    }

    // ==================== NODE SERIALIZATION TESTS ====================

    @Test
    void testSerializeNode_ProjectFile() {
        Path projectRoot = Paths.get("/project");
        Path filePath = Paths.get("/project/src/Main.java");
        ProjectFile projectFile = new ProjectFile(filePath, projectRoot);
        projectFile.setProperty("test.property", "test-value");
        projectFile.enableTag("java");

        String json = service.serializeNode(projectFile);

        assertNotNull(json);
        assertTrue(json.contains("\"@type\":\"ProjectFile\""));
        assertTrue(json.contains("src/Main.java"));
    }

    @Test
    void testSerializeNode_NullNode() {
        assertThrows(JsonSerializationService.SerializationException.class, () -> {
            service.serializeNode(null);
        });
    }

    @Test
    void testDeserializeNode_ProjectFile() {
        // Create a ProjectFile and serialize it
        Path projectRoot = Paths.get("/project");
        Path filePath = Paths.get("/project/src/Test.java");
        ProjectFile original = new ProjectFile(filePath, projectRoot);
        original.setProperty("language", "java");
        original.enableTag("source");

        String json = service.serializeNode(original);

        // Deserialize it back
        GraphNode deserialized = service.deserializeNode(json, GraphNode.class);

        assertNotNull(deserialized);
        assertTrue(deserialized instanceof ProjectFile);

        ProjectFile deserializedFile = (ProjectFile) deserialized;
        assertEquals("file", deserializedFile.getNodeType());
        assertTrue(deserializedFile.hasTag("source"));
    }

    @Test
    void testDeserializeNode_PolymorphicDetection() {
        // Serialize a ProjectFile
        Path projectRoot = Paths.get("/test");
        Path filePath = Paths.get("/test/Example.java");
        ProjectFile projectFile = new ProjectFile(filePath, projectRoot);

        String json = service.serializeNode(projectFile);

        // Deserialize using base GraphNode class - should automatically detect type
        GraphNode node = service.deserializeNode(json);

        assertNotNull(node);
        assertTrue(node instanceof ProjectFile, "Should deserialize as ProjectFile due to @JsonTypeInfo");
        assertEquals("file", node.getNodeType());
    }

    @Test
    void testDeserializeNode_NullJson() {
        assertThrows(JsonSerializationService.SerializationException.class, () -> {
            service.deserializeNode(null, GraphNode.class);
        });
    }

    @Test
    void testDeserializeNode_EmptyJson() {
        assertThrows(JsonSerializationService.SerializationException.class, () -> {
            service.deserializeNode("", GraphNode.class);
        });
    }

    // ==================== UTILITY METHOD TESTS ====================

    @Test
    void testConvertValue_StringToInteger() {
        Integer result = service.convertValue("42", Integer.class);
        assertEquals(42, result);
    }

    @Test
    void testConvertValue_MapToCustomType() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        @SuppressWarnings("unchecked")
        Map<String, String> result = service.convertValue(map, Map.class);

        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testConvertValue_NullValue() {
        Integer result = service.convertValue(null, Integer.class);
        assertNull(result);
    }

    @Test
    void testConvertValue_InvalidConversion() {
        // Converting an object that can't be converted should return null (with warning logged)
        String result = service.convertValue(new Object(), String.class);
        // The service logs a warning and returns null for invalid conversions
        assertNull(result);
    }

    @Test
    void testPrettyPrint_ValidJson() {
        String compactJson = "{\"name\":\"test\",\"value\":42}";
        String prettyJson = service.prettyPrint(compactJson);

        assertNotNull(prettyJson);
        assertTrue(prettyJson.contains("\n")); // Should have line breaks
        assertTrue(prettyJson.contains("\"name\""));
        assertTrue(prettyJson.contains("\"value\""));
    }

    @Test
    void testPrettyPrint_InvalidJson() {
        String invalidJson = "{invalid}";
        String result = service.prettyPrint(invalidJson);

        // Should return original string if pretty-print fails
        assertEquals(invalidJson, result);
    }

    @Test
    void testGetObjectMapper_NotNull() {
        assertNotNull(service.getObjectMapper());
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    void testRoundTrip_ComplexProjectFile() {
        // Create a complex ProjectFile with multiple properties and tags
        Path projectRoot = Paths.get("/home/user/project");
        Path filePath = Paths.get("/home/user/project/src/main/java/com/example/Service.java");
        ProjectFile original = new ProjectFile(filePath, projectRoot);

        original.setProperty("language", "java");
        original.setProperty("complexity", 15);
        original.setProperty("linesOfCode", 250);
        original.enableTag("service");
        original.enableTag("business-logic");

        // Serialize
        String json = service.serializeNode(original);
        assertNotNull(json);

        // Deserialize
        GraphNode deserialized = service.deserializeNode(json, GraphNode.class);
        assertNotNull(deserialized);
        assertTrue(deserialized instanceof ProjectFile);

        ProjectFile deserializedFile = (ProjectFile) deserialized;

        // Verify properties
        assertEquals("java", deserializedFile.getProperty("language"));
        assertEquals(15, ((Number) deserializedFile.getProperty("complexity")).intValue());
        assertEquals(250, ((Number) deserializedFile.getProperty("linesOfCode")).intValue());

        // Verify tags
        assertTrue(deserializedFile.hasTag("service"));
        assertTrue(deserializedFile.hasTag("business-logic"));

        // Verify basic attributes
        assertEquals("file", deserializedFile.getNodeType());
        assertTrue(deserializedFile.getId().contains("Service.java"));
    }

    @Test
    void testConsistency_SameInputSameOutput() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", 42);

        String json1 = service.serializeProperties(properties);
        String json2 = service.serializeProperties(properties);

        // Same input should produce same JSON
        assertEquals(json1, json2);
    }
}
