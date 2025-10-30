package com.analyzer.core.serialization;

import com.analyzer.api.graph.GraphNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Centralized JSON serialization service for all graph nodes and properties.
 * Provides a single, consistent ObjectMapper configuration used throughout the
 * application.
 * 
 * This service handles:
 * - Properties serialization (Map to JSON string for database storage)
 * - Graph node serialization (for file export)
 * - Polymorphic deserialization (via GraphNode @JsonTypeInfo)
 */
public class JsonSerializationService {

    private static final Logger logger = LoggerFactory.getLogger(JsonSerializationService.class);

    private final ObjectMapper objectMapper;

    /**
     * Default constructor with standard configuration.
     */
    public JsonSerializationService() {
        this.objectMapper = createConfiguredMapper();
    }

    /**
     * Constructor allowing custom ObjectMapper (for testing).
     */
    public JsonSerializationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Create and configure the ObjectMapper with all necessary modules.
     */
    private static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time module
        mapper.registerModule(new JavaTimeModule());

        // Auto-discover and register all available modules
        mapper.findAndRegisterModules();

        return mapper;
    }

    // ==================== PROPERTIES SERIALIZATION ====================

    /**
     * Serialize a properties map to JSON string.
     * Used for storing properties in database or files.
     * 
     * @param properties the properties map to serialize
     * @return JSON string representation, or "{}" if null/empty
     */
    public String serializeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(properties);
        } catch (Exception e) {
            logger.error("Failed to serialize properties to JSON: {}", properties.keySet(), e);
            return "{}";
        }
    }

    /**
     * Deserialize properties from JSON string to Map.
     * 
     * @param json the JSON string to deserialize
     * @return properties map, or empty map if null/invalid
     */
    public Map<String, Object> deserializeProperties(String json) {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            logger.error("Failed to deserialize properties from JSON: {}", json, e);
            return new HashMap<>();
        }
    }

    // ==================== TAG SERIALIZATION ====================

    /**
     * Serialize a set of tags to JSON array string.
     * Used for storing tags in database.
     * 
     * @param tags the tags set to serialize
     * @return JSON array string representation, or "[]" if null/empty
     */
    public String serializeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }

        try {
            // Convert to sorted list for consistent ordering
            List<String> sortedTags = new ArrayList<>(tags);
            Collections.sort(sortedTags);
            return objectMapper.writeValueAsString(sortedTags);
        } catch (Exception e) {
            logger.error("Failed to serialize tags to JSON: {}", tags, e);
            return "[]";
        }
    }

    /**
     * Deserialize tags from JSON array string to Set.
     * 
     * @param json the JSON array string to deserialize
     * @return tags set, or empty set if null/invalid
     */
    public Set<String> deserializeTags(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return new HashSet<>();
        }

        try {
            List<String> tagList = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return new HashSet<>(tagList);
        } catch (Exception e) {
            logger.error("Failed to deserialize tags from JSON: {}", json, e);
            return new HashSet<>();
        }
    }

    // ==================== NODE SERIALIZATION ====================

    /**
     * Serialize a GraphNode to JSON string.
     * Uses polymorphic type information from @JsonTypeInfo on GraphNode interface.
     * 
     * @param node the graph node to serialize
     * @return JSON string representation
     * @throws SerializationException if serialization fails
     */
    public String serializeNode(GraphNode node) {
        if (node == null) {
            throw new SerializationException("Cannot serialize null node");
        }

        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            String message = String.format("Failed to serialize node: %s (type: %s)",
                    node.getId(), node.getNodeType());
            logger.error(message, e);
            throw new SerializationException(message, e);
        }
    }

    /**
     * Deserialize a GraphNode from JSON string.
     * Automatically determines concrete type via @JsonTypeInfo.
     * 
     * @param json         the JSON string
     * @param expectedType the expected node type class
     * @param <T>          the node type
     * @return deserialized node
     * @throws SerializationException if deserialization fails
     */
    public <T extends GraphNode> T deserializeNode(String json, Class<T> expectedType) {
        if (json == null || json.trim().isEmpty()) {
            throw new SerializationException("Cannot deserialize null or empty JSON");
        }

        try {
            return objectMapper.readValue(json, expectedType);
        } catch (Exception e) {
            String message = String.format("Failed to deserialize node of type: %s",
                    expectedType.getSimpleName());
            logger.error(message + " from JSON: {}", json, e);
            throw new SerializationException(message, e);
        }
    }

    /**
     * Deserialize a GraphNode from JSON string with polymorphic type detection.
     * Uses the @JsonTypeInfo property to determine concrete type.
     * 
     * @param json the JSON string
     * @return deserialized node (actual type determined from JSON)
     * @throws SerializationException if deserialization fails
     */
    public GraphNode deserializeNode(String json) {
        return deserializeNode(json, GraphNode.class);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Convert a generic Object to a specific type.
     * Useful for property value conversion.
     * 
     * @param value      the value to convert
     * @param targetType the target type class
     * @param <T>        the target type
     * @return converted value
     */
    public <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception e) {
            logger.warn("Failed to convert value to {}: {}", targetType.getSimpleName(), value, e);
            return null;
        }
    }

    /**
     * Convert a generic Object to a specific type using TypeReference.
     * 
     * @param value         the value to convert
     * @param typeReference the target type reference
     * @param <T>           the target type
     * @return converted value
     */
    public <T> T convertValue(Object value, TypeReference<T> typeReference) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.convertValue(value, typeReference);
        } catch (Exception e) {
            logger.warn("Failed to convert value using TypeReference: {}", value, e);
            return null;
        }
    }

    /**
     * Pretty-print JSON string for debugging.
     * 
     * @param json the JSON string to format
     * @return pretty-printed JSON string
     */
    public String prettyPrint(String json) {
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            logger.warn("Failed to pretty-print JSON", e);
            return json;
        }
    }

    /**
     * Get the underlying ObjectMapper.
     * Use with caution - prefer using service methods.
     * 
     * @return the configured ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    // ==================== EXCEPTION CLASS ====================

    /**
     * Exception thrown when serialization/deserialization fails.
     */
    public static class SerializationException extends RuntimeException {
        public SerializationException(String message) {
            super(message);
        }

        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
