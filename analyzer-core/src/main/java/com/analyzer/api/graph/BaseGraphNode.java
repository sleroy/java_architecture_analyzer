package com.analyzer.api.graph;

import com.analyzer.api.metrics.Metrics;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

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

    private final Map<String, Double> metrics = new HashMap<>();
    private final Map<String, Object> properties = new HashMap<>();
    private final Set<String> tags = new TreeSet<>();
    @JsonProperty("id")
    private final String nodeId;
    private final String nodeType;

    /**
     * Creates a new BaseGraphNode with the specified ID and type.
     *
     * @param nodeId   Unique identifier for this node
     * @param nodeType Type classification for this node
     */
    protected BaseGraphNode(final String nodeId, final String nodeType) {
        this.nodeId = Objects.requireNonNull(nodeId, "Node ID cannot be null");
        this.nodeType = Objects.requireNonNull(nodeType, "Node type cannot be null");
    }

    @Override
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String getNodeId() {
        return nodeId;
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
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Map<String, Object> getNodeProperties() {
        return Map.copyOf(properties);
    }

    @Override
    public void enableTag(final String tag) {
        tags.add(tag);
    }

    @Override
    public boolean hasTag(final String tag) {
        return tags.contains(tag);
    }

    @Override
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    @Override
    public void removeTag(final String tag) {
        tags.remove(tag);
    }

    public boolean hasAllTags(final String[] tags) {
        if (tags == null || tags.length == 0)
            return true;
        return Arrays.stream(tags).allMatch(this::hasTag);
    }

    /**
     * Get the metrics interface for this node.
     * Metrics are stored in a separate map from properties.
     *
     * @return Metrics interface for reading/writing metrics
     */
    public Metrics getMetrics() {
        return new NodeMetrics();
    }

    /**
     * Gets a property value with the expected type.
     *
     * @param key          Property key
     * @param expectedType Expected value type
     * @param defaultValue Default value if property is missing or wrong type
     * @return Property value cast to expected type, or default value
     */
    @Override
    public <T> T getProperty(final String key, final Class<T> expectedType, final T defaultValue) {
        final Object value = properties.get(key);
        if (expectedType.isInstance(value)) {
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
    @Override
    public String getStringProperty(final String key, final String defaultValue) {
        return getProperty(key, String.class, defaultValue);
    }

    /**
     * Gets an integer property value.
     *
     * @param key          Property key
     * @param defaultValue Default value if property is missing
     * @return Property value or default
     */
    @Override
    public int getIntProperty(final String key, final int defaultValue) {
        return getProperty(key, Integer.class, defaultValue);
    }

    /**
     * Checks if a property exists and is not null.
     *
     * @param key Property key
     * @return true if property exists and is not null
     */
    @Override
    public boolean hasProperty(final String key) {
        return properties.containsKey(key) && properties.get(key) != null;
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
    @Override
    public <T> T getProperty(final java.lang.String propertyKey) {
        return (T) properties.get(propertyKey);
    }

    /**
     * Get metrics map for JSON serialization.
     *
     * @return metrics map
     */
    @JsonProperty("metrics")
    @Override
    public Map<String, Double> getMetricsMap() {
        return Map.copyOf(metrics);
    }

    /**
     * Set metrics map for JSON deserialization.
     *
     * @param metricsMap metrics to set
     */
    @JsonProperty("metrics")
    public void setMetricsMap(final Map<String, Double> metricsMap) {
        metrics.clear();
        if (metricsMap != null) {
            metrics.putAll(metricsMap);
        }
    }

    /**
     * Sets a property value for this node.
     *
     * @param key   Property key
     * @param value Property value (null values remove the property)
     */
    @Override
    public void setProperty(final String key, final Object value) {
        Objects.requireNonNull(key, "Property key cannot be null");
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

    /**
     * Gets a boolean property value.
     *
     * @param key          Property key
     * @param defaultValue Default value if property is missing
     * @return Property value or default
     */
    @Override
    public boolean getBooleanProperty(final String key, final boolean defaultValue) {
        return getProperty(key, Boolean.class, defaultValue);
    }

    /**
     * Gets all property keys for this node.
     *
     * @return Set of property keys
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.Set<String> getPropertyKeys() {
        return new java.util.HashSet<>(properties.keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        final BaseGraphNode that = (BaseGraphNode) obj;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public String toString() {
        return String.format("%s{id='%s', type='%s', properties=%d}",
                getClass().getSimpleName(), nodeId, nodeType, properties.size());
    }

    private class NodeMetrics implements Metrics {
        @Override
        public Number getMetric(final String metricName) {
            return metrics.getOrDefault(metricName, 0.0d);
        }

        @Override
        public void setMetric(final String metricName, final Number value) {
            if (value == null) {
                metrics.remove(metricName);
            } else {
                metrics.put(metricName, value.doubleValue());
            }
        }

        @Override
        public void setMaxMetric(final String metricName, final Number value) {
            if (value != null) {
                final double newValue = value.doubleValue();
                final double currentValue = metrics.getOrDefault(metricName, 0.0);
                if (newValue > currentValue) {
                    metrics.put(metricName, newValue);
                }
            }
        }

        @Override
        public Map<String, Double> getAllMetrics() {
            return Collections.unmodifiableMap(metrics);
        }
    }
}
