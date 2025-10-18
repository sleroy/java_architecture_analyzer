package com.analyzer.core.graph;

import com.analyzer.core.model.ProjectFile;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Interface representing a node in the analysis graph.
 * This interface is designed to be implemented directly by domain objects like
 * ProjectFile and Package.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProjectFile.class, name = "ProjectFile"),
    @JsonSubTypes.Type(value = JavaClassNode.class, name = "JavaClassNode")
})
public interface GraphNode {

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
}
