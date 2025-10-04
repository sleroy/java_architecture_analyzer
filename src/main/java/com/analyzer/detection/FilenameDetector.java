package com.analyzer.detection;

import com.analyzer.core.ProjectFile;
import java.nio.file.Path;
import java.util.*;

/**
 * Detects files based on their exact filename.
 * Useful for detecting build files, configuration files, etc.
 */
public class FilenameDetector implements FileDetector {

    private final String name;
    private final String tag;
    private final Set<String> filenames;
    private final int priority;
    private final boolean caseSensitive;

    public FilenameDetector(String name, String tag, Collection<String> filenames) {
        this(name, tag, filenames, 0, false);
    }

    public FilenameDetector(String name, String tag, Collection<String> filenames, int priority,
            boolean caseSensitive) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.tag = Objects.requireNonNull(tag, "Tag cannot be null");
        this.priority = priority;
        this.caseSensitive = caseSensitive;
        this.filenames = new HashSet<>();

        // Normalize filenames based on case sensitivity
        if (filenames != null) {
            for (String filename : filenames) {
                if (filename != null && !filename.trim().isEmpty()) {
                    String normalized = filename.trim();
                    if (!caseSensitive) {
                        normalized = normalized.toLowerCase();
                    }
                    this.filenames.add(normalized);
                }
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public boolean matches(Path filePath, Path projectRoot) {
        if (filePath == null) {
            return false;
        }

        String fileName = filePath.getFileName().toString();
        String checkName = caseSensitive ? fileName : fileName.toLowerCase();
        return filenames.contains(checkName);
    }

    @Override
    public void processFile(ProjectFile projectFile) {
        // Set the detector tag
        projectFile.setTag("detector." + getTag(), true);
        projectFile.setTag("fileType", getTag());

        // Mark as special filename
        projectFile.setTag("specialFile", true);
        projectFile.setTag("filename." + projectFile.getFileName().toLowerCase(), true);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String getDescription() {
        return String.format("Filename detector '%s' for files: %s", name, filenames);
    }

    /**
     * Get the filenames this detector matches
     */
    public Set<String> getFilenames() {
        return Collections.unmodifiableSet(filenames);
    }

    /**
     * Create a detector for build files
     */
    public static FilenameDetector createBuildFileDetector() {
        return new FilenameDetector(
                "Build Files",
                "build",
                Arrays.asList("pom.xml", "build.gradle", "build.xml", "Makefile", "CMakeLists.txt", "package.json"),
                15,
                false);
    }

    /**
     * Create a detector for readme files
     */
    public static FilenameDetector createReadmeDetector() {
        return new FilenameDetector(
                "Documentation Files",
                "docs",
                Arrays.asList("README", "README.md", "README.txt", "CHANGELOG", "CHANGELOG.md", "LICENSE",
                        "LICENSE.txt"),
                5,
                false);
    }

    /**
     * Create a detector for Docker files
     */
    public static FilenameDetector createDockerDetector() {
        return new FilenameDetector(
                "Docker Files",
                "docker",
                Arrays.asList("Dockerfile", "docker-compose.yml", "docker-compose.yaml", ".dockerignore"),
                10,
                true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FilenameDetector that = (FilenameDetector) o;
        return Objects.equals(name, that.name) && Objects.equals(filenames, that.filenames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, filenames);
    }

    @Override
    public String toString() {
        return "FilenameDetector{" +
                "name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", filenames=" + filenames +
                ", priority=" + priority +
                ", caseSensitive=" + caseSensitive +
                '}';
    }
}
