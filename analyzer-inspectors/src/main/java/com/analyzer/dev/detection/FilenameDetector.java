package com.analyzer.dev.detection;

import com.analyzer.api.detector.FileDetector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.model.ProjectFile;

import java.util.*;

/**
 * Inspector that detects files based on their exact filename.
 * Useful for detecting build files, configuration files, etc.
 *
 * <p>
 * As a fundamental detector, this inspector has no dependencies and operates
 * directly on file names.
 * </p>
 */
public class FilenameDetector implements FileDetector {

    public static class TAGS {
        public static final String TAG_FILENAME_DETECTED = "filename.";

    }

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

    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null || projectFile.getFileName() == null) {
            return false;
        }

        String fileName = projectFile.getFileName();
        String checkName = caseSensitive ? fileName : fileName.toLowerCase();
        return filenames.contains(checkName);
    }


    public void detect(NodeDecorator<ProjectFile> projectFileDecorator) {
        // Set the detector tag using own TAGS constants
        projectFileDecorator.enableTag(TAGS.TAG_FILENAME_DETECTED + getTag());
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the tag used by this inspector
     */
    public String getTag() {
        return tag;
    }

    /**
     * Create an inspector for build files
     */
    public static FilenameDetector createBuildFileInspector() {
        return new FilenameDetector(
                "Build Files",
                "build",
                Arrays.asList("pom.xml", "build.gradle", "build.xml", "Makefile", "CMakeLists.txt", "package.json"),
                15,
                false);
    }

    /**
     * Create an inspector for readme files
     */
    public static FilenameDetector createReadmeInspector() {
        return new FilenameDetector(
                "Documentation Files",
                "docs",
                Arrays.asList("README", "README.md", "README.txt", "CHANGELOG", "CHANGELOG.md", "LICENSE",
                        "LICENSE.txt"),
                5,
                false);
    }

    /**
     * Create an inspector for Docker files
     */
    public static FilenameDetector createDockerInspector() {
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
