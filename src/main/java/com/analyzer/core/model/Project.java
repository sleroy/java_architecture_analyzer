package com.analyzer.core.model;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a project being analyzed.
 * Contains ProjectFiles discovered through FileDetectors and stores analysis
 * results.
 * Replaces the previous class-centric approach with a more general file-based
 * approach.
 */
public class Project {
    public static final String DEFAULT_FILE_NAME = "project-analysis.json";
    private final Path projectPath;
    private final String projectName;
    private final Map<String, ProjectFile> projectFiles;
    private final Map<String, Object> projectData;
    private final Date createdAt;
    private Date lastAnalyzed;

    public Project(Path projectPath) {
        this.projectPath = Objects.requireNonNull(projectPath, "Project path cannot be null");
        this.projectName = projectPath.getFileName().toString();
        this.projectFiles = new ConcurrentHashMap<>();
        this.projectData = new ConcurrentHashMap<>();
        this.createdAt = new Date();
        this.lastAnalyzed = null;
    }

    public Project(Path projectPath, String projectName) {
        this.projectPath = Objects.requireNonNull(projectPath, "Project path cannot be null");
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null");
        this.projectFiles = new ConcurrentHashMap<>();
        this.projectData = new ConcurrentHashMap<>();
        this.createdAt = new Date();
        this.lastAnalyzed = null;
    }

    /**
     * Get the root path of the project
     */
    public Path getProjectPath() {
        return projectPath;
    }

    /**
     * Get the project name
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Get all project files discovered in this project
     */
    public Map<String, ProjectFile> getProjectFiles() {
        return Collections.unmodifiableMap(projectFiles);
    }

    /**
     * Add a project file to the project
     */
    public void addProjectFile(ProjectFile projectFile) {
        Objects.requireNonNull(projectFile, "ProjectFile cannot be null");
        projectFiles.put(projectFile.getRelativePath(), projectFile);
    }

    /**
     * Remove a project file from the project
     */
    public void removeProjectFile(String relativePath) {
        projectFiles.remove(relativePath);
    }

    /**
     * Get a specific project file by its relative path
     */
    public ProjectFile getProjectFile(String relativePath) {
        return projectFiles.get(relativePath);
    }

    /**
     * Get or create a project file with the given relative path and absolute path.
     * If the file doesn't exist, creates a new ProjectFile and adds it to the
     * project.
     */
    public ProjectFile getOrCreateProjectFile(String relativePath, Path absolutePath) {
        return projectFiles.computeIfAbsent(relativePath, key -> new ProjectFile(absolutePath, this.projectPath));
    }

    /**
     * Get project files filtered by tag
     */
    public List<ProjectFile> getProjectFilesByTag(String tagName, Object tagValue) {
        return projectFiles.values().stream()
                .filter(file -> Objects.equals(file.getProperty(tagName), tagValue))
                .toList();
    }

    /**
     * Get project files that have a specific tag (regardless of value)
     */
    public List<ProjectFile> getProjectFilesWithTag(String tagName) {
        return projectFiles.values().stream()
                .filter(file -> file.hasProperty(tagName))
                .toList();
    }

    /**
     * Store project-level analysis data
     */
    public void setProjectData(String key, Object value) {
        if (value == null) {
            projectData.remove(key);
        } else {
            projectData.put(key, value);
        }
    }

    /**
     * Retrieve project-level analysis data
     */
    public Object getProjectData(String key) {
        return projectData.get(key);
    }

    /**
     * Get all project-level data
     */
    public Map<String, Object> getAllProjectData() {
        return Collections.unmodifiableMap(projectData);
    }

    /**
     * Check if project has specific data
     */
    public boolean hasProjectData(String key) {
        return projectData.containsKey(key);
    }

    /**
     * Get creation timestamp
     */
    public Date getCreatedAt() {
        return new Date(createdAt.getTime());
    }

    /**
     * Get last analysis timestamp
     */
    public Date getLastAnalyzed() {
        return lastAnalyzed != null ? new Date(lastAnalyzed.getTime()) : null;
    }

    /**
     * Update last analysis timestamp
     */
    public void updateLastAnalyzed() {
        this.lastAnalyzed = new Date();
    }

    /**
     * Get project statistics
     */
    public ProjectStatistics getStatistics() {
        Map<String, Integer> fileTypeCount = new HashMap<>();
        int totalFiles = projectFiles.size();

        // Count files by their detected type
        for (ProjectFile file : projectFiles.values()) {
            String fileType = (String) file.getProperty("fileType");
            if (fileType != null) {
                fileTypeCount.merge(fileType, 1, Integer::sum);
            }
        }

        return new ProjectStatistics(totalFiles, fileTypeCount, projectData.size());
    }

    /**
     * Clear all project files and data
     */
    public void clear() {
        projectFiles.clear();
        projectData.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Project project = (Project) o;
        return Objects.equals(projectPath, project.projectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectPath);
    }

    @Override
    public String toString() {
        return "Project{" +
                "projectPath=" + projectPath +
                ", projectName='" + projectName + '\'' +
                ", filesCount=" + projectFiles.size() +
                ", dataCount=" + projectData.size() +
                '}';
    }

    /**
     * Statistics about the project
     */
    public static class ProjectStatistics {
        private final int totalFiles;
        private final Map<String, Integer> fileTypeCount;
        private final int projectDataCount;

        public ProjectStatistics(int totalFiles, Map<String, Integer> fileTypeCount, int projectDataCount) {
            this.totalFiles = totalFiles;
            this.fileTypeCount = new HashMap<>(fileTypeCount);
            this.projectDataCount = projectDataCount;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        public Map<String, Integer> getFileTypeCount() {
            return Collections.unmodifiableMap(fileTypeCount);
        }

        public int getProjectDataCount() {
            return projectDataCount;
        }

        @Override
        public String toString() {
            return String.format("ProjectStatistics{totalFiles=%d, fileTypes=%s, projectData=%d}",
                    totalFiles, fileTypeCount, projectDataCount);
        }
    }
}
