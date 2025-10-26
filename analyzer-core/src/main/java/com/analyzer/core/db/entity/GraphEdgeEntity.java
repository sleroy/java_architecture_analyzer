package com.analyzer.core.db.entity;

import java.time.LocalDateTime;

/**
 * Database entity representing a graph edge (relationship between nodes).
 * Maps to the 'edges' table in H2 database.
 */
public class GraphEdgeEntity {

    private Long id;
    private String sourceId;
    private String targetId;
    private String edgeType; // depends_on, contains, extends, implements, etc.
    private String metadataJson; // Optional edge metadata
    private LocalDateTime createdAt;

    public GraphEdgeEntity() {
    }

    public GraphEdgeEntity(String sourceId, String targetId, String edgeType) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.edgeType = edgeType;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "GraphEdgeEntity{" +
                "id=" + id +
                ", sourceId='" + sourceId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", edgeType='" + edgeType + '\'' +
                '}';
    }
}
