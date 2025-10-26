package com.analyzer.core.db.entity;

import java.time.LocalDateTime;

/**
 * Entity representing a graph node in the database.
 * Maps to the 'nodes' table with JSON properties.
 */
public class GraphNodeEntity {

    private String id;
    private String nodeType;
    private String displayLabel;
    private String properties;  // JSON string for flexible properties
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GraphNodeEntity() {
    }

    public GraphNodeEntity(String id, String nodeType, String displayLabel, String properties) {
        this.id = id;
        this.nodeType = nodeType;
        this.displayLabel = displayLabel;
        this.properties = properties;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "GraphNodeEntity{" +
                "id='" + id + '\'' +
                ", nodeType='" + nodeType + '\'' +
                ", displayLabel='" + displayLabel + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
