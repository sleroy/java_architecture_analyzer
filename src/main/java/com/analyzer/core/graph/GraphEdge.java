package com.analyzer.core.graph;
import com.analyzer.core.inspector.InspectorDependencies;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an edge between two nodes in the analysis graph.
 * Edges have automatically generated IDs and support custom properties/tags.
 */
public class GraphEdge {

    private final String id;
    private final GraphNode source;
    private final GraphNode target;
    private final String edgeType;
    private final Map<String, Object> properties;

    /**
     * Creates a new graph edge with an auto-generated ID.
     * 
     * @param source   the source node
     * @param target   the target node
     * @param edgeType the type of relationship (e.g., "depends_on", "contains",
     *                 "implements")
     */
    public GraphEdge(GraphNode source, GraphNode target, String edgeType) {
        this(source, target, edgeType, new HashMap<>());
    }

    /**
     * Creates a new graph edge with an auto-generated ID and properties.
     * 
     * @param source     the source node
     * @param target     the target node
     * @param edgeType   the type of relationship
     * @param properties additional properties/tags for this edge
     */
    public GraphEdge(GraphNode source, GraphNode target, String edgeType, Map<String, Object> properties) {
        this.id = generateId();
        this.source = Objects.requireNonNull(source, "Source node cannot be null");
        this.target = Objects.requireNonNull(target, "Target node cannot be null");
        this.edgeType = Objects.requireNonNull(edgeType, "Edge type cannot be null");
        this.properties = new HashMap<>(properties);
    }

    /**
     * Creates a new graph edge with a specific ID (for internal use and JSON deserialization).
     * 
     * @param id         the edge ID
     * @param source     the source node
     * @param target     the target node
     * @param edgeType   the type of relationship
     * @param properties additional properties/tags for this edge
     */
    @JsonCreator
    protected GraphEdge(@JsonProperty("id") String id, 
                       @JsonProperty("source") GraphNode source, 
                       @JsonProperty("target") GraphNode target, 
                       @JsonProperty("edgeType") String edgeType,
                       @JsonProperty("properties") Map<String, Object> properties) {
        this.id = Objects.requireNonNull(id, "Edge ID cannot be null");
        this.source = Objects.requireNonNull(source, "Source node cannot be null");
        this.target = Objects.requireNonNull(target, "Target node cannot be null");
        this.edgeType = Objects.requireNonNull(edgeType, "Edge type cannot be null");
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    /**
     * Generates a unique ID for this edge.
     */
    private String generateId() {
        return "edge_" + UUID.randomUUID().toString().replace("-", "");
    }

    public String getId() {
        return id;
    }

    public GraphNode getSource() {
        return source;
    }

    public GraphNode getTarget() {
        return target;
    }

    public String getEdgeType() {
        return edgeType;
    }

    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    /**
     * Adds or updates a property for this edge.
     * 
     * @param key   the property key
     * @param value the property value
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * Gets a property value.
     * 
     * @param key the property key
     * @return the property value, or null if not found
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Checks if this edge has a specific property.
     * 
     * @param key the property key
     * @return true if the property exists
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GraphEdge graphEdge = (GraphEdge) o;
        return Objects.equals(id, graphEdge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("GraphEdge{id='%s', source='%s', target='%s', type='%s', properties=%d}",
                id, source.getId(), target.getId(), edgeType, properties.size());
    }
}
