package com.analyzer.core.model;

import com.analyzer.core.graph.ProjectFileRepository;

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
    private final ProjectFileRepository projectFileRepository;

    public Project(Path projectPath) {
        this(projectPath, projectPath.getFileName().toString(), null);
    }

    public Project(Path projectPath, String projectName) {
        this(projectPath, projectName, null);
    }

    public Project(Path projectPath, String projectName, ProjectFileRepository projectFileRepository) {
        this.projectPath = Objects.requireNonNull(projectPath, "Project path cannot be null");
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null");
        this.projectFiles = new ConcurrentHashMap<>();
        this.projectData = new ConcurrentHashMap<>();
        this.createdAt = new Date();
        this.lastAnalyzed = null;
        this.projectFileRepository = projectFileRepository;
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
     * Add a project file to the project.
     * Uses absolute path (ID) as key to ensure uniqueness.
     */
    public void addProjectFile(ProjectFile projectFile) {
        Objects.requireNonNull(projectFile, "ProjectFile cannot be null");
        String id = projectFile.getId(); // Use absolute path as key for uniqueness
        projectFiles.put(id, projectFile);
    }

    /**
     * Remove a project file from the project by its absolute path (ID)
     */
    public void removeProjectFile(String absolutePath) {
        projectFiles.remove(absolutePath);
    }

    /**
     * Get a specific project file by its absolute path (ID)
     */
    public ProjectFile getProjectFile(String absolutePath) {
        return projectFiles.get(absolutePath);
    }

    /**
     * Get or create a project file with the given absolute path.
     * If the file doesn't exist, creates a new ProjectFile and adds it to the
     * project.
     * Uses absolute path as key to ensure uniqueness across the project.
     * 
     * If a ProjectFileRepository is configured, the file will be registered in the
     * graph store.
     */
    public ProjectFile getOrCreateProjectFile(String relativePath, Path absolutePath) {
        String id = absolutePath.toString(); // Use absolute path as key

        // First check local cache
        ProjectFile projectFile = projectFiles.get(id);
        if (projectFile != null) {
            return projectFile;
        }

        // If repository is available, try to find or create through it
        if (projectFileRepository != null) {
            projectFile = projectFileRepository.findByPath(absolutePath).orElse(null);
            if (projectFile == null) {
                // Create new ProjectFile and save to repository
                projectFile = new ProjectFile(absolutePath, this.projectPath);
                projectFileRepository.save(projectFile);
            }
            // Add to local cache
            projectFiles.put(id, projectFile);
            return projectFile;
        }

        // Fallback: no repository available, use local cache only
        return projectFiles.computeIfAbsent(id, key -> new ProjectFile(absolutePath, this.projectPath));
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
