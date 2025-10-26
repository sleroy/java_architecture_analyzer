package com.analyzer.core.db.entity;

import java.time.LocalDateTime;

/**
 * Database entity representing a project.
 * Maps to the 'projects' table in H2 database.
 */
public class ProjectEntity {

    private Long id;
    private String name;
    private String rootPath;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProjectEntity() {
    }

    public ProjectEntity(String name, String rootPath, String description) {
        this.name = name;
        this.rootPath = rootPath;
        this.description = description;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        return "ProjectEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", rootPath='" + rootPath + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
