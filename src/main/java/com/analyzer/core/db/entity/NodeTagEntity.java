package com.analyzer.core.db.entity;

import java.time.LocalDateTime;

/**
 * Database entity representing a node tag.
 * Maps to the 'node_tags' table in H2 database.
 */
public class NodeTagEntity {

    private String nodeId;
    private String tag;
    private LocalDateTime createdAt;

    public NodeTagEntity() {
    }

    public NodeTagEntity(String nodeId, String tag) {
        this.nodeId = nodeId;
        this.tag = tag;
    }

    // Getters and Setters

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "NodeTagEntity{" +
                "nodeId='" + nodeId + '\'' +
                ", tag='" + tag + '\'' +
                '}';
    }
}
