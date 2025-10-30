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
    private String properties; // JSON string for flexible properties
    private String metricsMap; // JSON string for metrics
    private String tags; // JSON array string for tags
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

    public GraphNodeEntity(String id, String nodeType, String displayLabel, String properties, String metricsMap) {
        this.id = id;
        this.nodeType = nodeType;
        this.displayLabel = displayLabel;
        this.properties = properties;
        this.metricsMap = metricsMap;
    }

    public GraphNodeEntity(String id, String nodeType, String displayLabel, String properties, String metricsMap,
            String tags) {
        this.id = id;
        this.nodeType = nodeType;
        this.displayLabel = displayLabel;
        this.properties = properties;
        this.metricsMap = metricsMap;
        this.tags = tags;
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

    public String getMetricsMap() {
        return metricsMap;
    }

    public void setMetricsMap(String metricsMap) {
        this.metricsMap = metricsMap;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
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
