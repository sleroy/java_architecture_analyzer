package com.analyzer.api.graph;

import com.analyzer.api.metrics.Metrics;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.model.Package;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;
import java.util.Set;

/**
 * Interface representing a node in the analysis graph.
 * This interface is designed to be implemented directly by domain objects like
 * ProjectFile and Package.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProjectFile.class, name = "ProjectFile"),
        @JsonSubTypes.Type(value = JavaClassNode.class, name = "JavaClassNode"),
        @JsonSubTypes.Type(value = Package.class, name = "Package")
})
public interface GraphNode {

    Map<String, Object> getProperties();

    String getNodeId();

    /**
     * Gets the unique identifier for this node.
     * 
     * @return the node ID
     */
    String getId();

    /**
     * Gets the type of this node (e.g., "file", "class", "package", "method").
     * 
     * @return the node type as a string
     */
    String getNodeType();

    /**
     * Gets additional properties/tags associated with this node.
     * These properties can be used for filtering and analysis.
     * 
     * @return map of property name to value
     */
    Map<String, Object> getNodeProperties();

    /**
     * Gets a human-readable label for this node.
     * This is typically used for display purposes in graph visualizations.
     * 
     * @return the display label
     */
    String getDisplayLabel();

    /**
     * Adds a tag to this node.
     *
     * @param tag the tag to add
     */
    void enableTag(String tag);

    /**
     * Checks if this node has a specific tag.
     *
     * @param tag the tag to check for
     * @return true if the node has the tag, false otherwise
     */
    boolean hasTag(String tag);

    /**
     * Gets all tags associated with this node.
     *
     * @return a set of tags
     */
    Set<String> getTags();

    /**
     * Removes a tag from this node.
     *
     * @param tag the tag to remove
     */
    void removeTag(String tag);

    boolean hasAllTags(String[] tagArray);

    /**
     * Returns the list of metrics associate to the node.
     * 
     * @return the metrics
     */
    Metrics getMetrics();

    <T> T getProperty(String key, Class<T> expectedType, T defaultValue);

    String getStringProperty(String key, String defaultValue);

    String getPropertyToString(String key);

    int getIntProperty(String key, int defaultValue);

    boolean hasProperty(String key);

    @SuppressWarnings("unchecked")
    <T> T getProperty(String propertyKey);

    @JsonProperty("metrics")
    Map<String, Double> getMetricsMap();

    void setProperty(String key, Object value);

    boolean getBooleanProperty(String key, boolean defaultValue);
}
