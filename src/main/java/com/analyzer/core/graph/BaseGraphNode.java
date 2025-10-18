package com.analyzer.core.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract base class for graph nodes that provides common property management
 * functionality. This class extracts shared behavior from ProjectFile and
 * provides
 * a foundation for all graph node implementations.
 *
 * <p>
 * Provides:
 * </p>
 * <ul>
 * <li>Property storage and retrieval with type safety</li>
 * <li>Basic GraphNode interface implementation</li>
 * <li>Standard equals/hashCode based on node ID</li>
 * </ul>
 */
public abstract class BaseGraphNode implements GraphNode {

    private final Map<String, Object> properties;
    private final String nodeId;
    private final String nodeType;

    /**
     * Creates a new BaseGraphNode with the specified ID and type.
     *
     * @param nodeId   Unique identifier for this node
     * @param nodeType Type classification for this node
     */
    protected BaseGraphNode(String nodeId, String nodeType) {
        this.nodeId = Objects.requireNonNull(nodeId, "Node ID cannot be null");
        this.nodeType = Objects.requireNonNull(nodeType, "Node type cannot be null");
        this.properties = new HashMap<>();
    }

    @Override
    public String getId() {
        return nodeId;
    }

    @Override
    public String getNodeType() {
        return nodeType;
    }

    @Override
    public Map<String, Object> getNodeProperties() {
        return new HashMap<>(properties);
    }

    /**
     * Sets a property value for this node.
     *
     * @param key   Property key
     * @param value Property value (null values remove the property)
     */
    protected void setProperty(String key, Object value) {
        Objects.requireNonNull(key, "Property key cannot be null");
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

    /**
     * Gets a property value with the expected type.
     *
     * @param key          Property key
     * @param expectedType Expected value type
     * @param defaultValue Default value if property is missing or wrong type
     * @return Property value cast to expected type, or default value
     */
    public <T> T getProperty(String key, Class<T> expectedType, T defaultValue) {
        Object value = properties.get(key);
        if (value != null && expectedType.isInstance(value)) {
            return expectedType.cast(value);
        }
        return defaultValue;
    }

    /**
     * Gets a string property value.
     *
     * @param key          Property key
     * @param defaultValue Default value if property is missing
     * @return Property value or default
     */
    protected String getStringProperty(String key, String defaultValue) {
        return getProperty(key, String.class, defaultValue);
    }

    /**
     * Gets a boolean property value.
     *
     * @param key          Property key
     * @param defaultValue Default value if property is missing
     * @return Property value or default
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        return getProperty(key, Boolean.class, defaultValue);
    }

    /**
     * Gets an integer property value.
     *
     * @param key          Property key
     * @param defaultValue Default value if property is missing
     * @return Property value or default
     */
    protected int getIntProperty(String key, int defaultValue) {
        return getProperty(key, Integer.class, defaultValue);
    }

    /**
     * Checks if a property exists and is not null.
     *
     * @param key Property key
     * @return true if property exists and is not null
     */
    protected boolean hasProperty(String key) {
        return properties.containsKey(key) && properties.get(key) != null;
    }

    /**
     * Gets all property keys for this node.
     *
     * @return Set of property keys
     */
    protected java.util.Set<String> getPropertyKeys() {
        return new java.util.HashSet<>(properties.keySet());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        BaseGraphNode that = (BaseGraphNode) obj;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public String toString() {
        return String.format("%s{id='%s', type='%s', properties=%d}",
                getClass().getSimpleName(), nodeId, nodeType, properties.size());
    }

    /**
     * Gets a property value by key with unchecked cast.
     * Use with caution - prefer type-safe getProperty methods when possible.
     *
     * @param <T>         Expected property type
     * @param propertyKey Property key
     * @return Property value cast to T, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(java.lang.String propertyKey) {
        return (T) properties.get(propertyKey);
    }
}
