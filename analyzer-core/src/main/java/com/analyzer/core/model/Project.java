package com.analyzer.core.model;

import com.analyzer.api.graph.ProjectFileRepository;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
     * Get or create a project file with the given absolute path.
     * Delegates to ProjectFileRepository for all file management.
     * 
     * @param relativePath not used, kept for backward compatibility
     * @param absolutePath the absolute path of the file
     * @return the ProjectFile instance from the repository
     */
    public ProjectFile getOrCreateProjectFile(String relativePath, Path absolutePath) {
        if (projectFileRepository == null) {
            throw new IllegalStateException("ProjectFileRepository is required but not configured");
        }

        return projectFileRepository.findByPath(absolutePath).orElseGet(() -> {
            ProjectFile projectFile = new ProjectFile(absolutePath, this.projectPath);
            projectFileRepository.save(projectFile);
            return projectFile;
        });
    }

    /**
     * Get project files filtered by tag.
     * Delegates to ProjectFileRepository.
     */
    public List<ProjectFile> getProjectFilesByTag(String tagName, Object tagValue) {
        if (projectFileRepository == null) {
            return Collections.emptyList();
        }
        return projectFileRepository.findAll().stream()
                .filter(file -> Objects.equals(file.getProperty(tagName), tagValue))
                .toList();
    }

    /**
     * Get project files that have a specific tag (regardless of value).
     * Delegates to ProjectFileRepository.
     */
    public List<ProjectFile> getProjectFilesWithTag(String tagName) {
        if (projectFileRepository == null) {
            return Collections.emptyList();
        }
        return projectFileRepository.findAll().stream()
                .filter(file -> file.hasProperty(tagName))
                .toList();
    }

    /**
     * Get all project files as a map (absolute path -> ProjectFile).
     * Delegates to ProjectFileRepository.
     * 
     * @return map of absolute path strings to ProjectFile objects
     */
    public Map<String, ProjectFile> getProjectFiles() {
        if (projectFileRepository == null) {
            return Collections.emptyMap();
        }
        return projectFileRepository.findAll().stream()
                .collect(Collectors.toMap(
                        file -> file.getFilePath().toString(),
                        file -> file));
    }

    /**
     * Add a project file to the repository.
     * Delegates to ProjectFileRepository.
     * 
     * @param projectFile the file to add
     */
    public void addProjectFile(ProjectFile projectFile) {
        if (projectFileRepository != null) {
            projectFileRepository.save(projectFile);
        }
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
     * Get project statistics.
     * Delegates to ProjectFileRepository for file counts.
     */
    public ProjectStatistics getStatistics() {
        Map<String, Integer> fileTypeCount = new HashMap<>();
        List<ProjectFile> allFiles = projectFileRepository != null
                ? projectFileRepository.findAll()
                : Collections.emptyList();
        int totalFiles = allFiles.size();

        // Count files by their detected type
        for (ProjectFile file : allFiles) {
            String fileType = (String) file.getProperty("fileType");
            if (fileType != null) {
                fileTypeCount.merge(fileType, 1, Integer::sum);
            }
        }

        return new ProjectStatistics(totalFiles, fileTypeCount, projectData.size());
    }

    /**
     * Clear all project data.
     * Note: Project files are managed by ProjectFileRepository and should be
     * cleared there if needed.
     */
    public void clear() {
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
        int fileCount = projectFileRepository != null ? projectFileRepository.findAll().size() : 0;
        return "Project{" +
                "projectPath=" + projectPath +
                ", projectName='" + projectName + '\'' +
                ", filesCount=" + fileCount +
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
