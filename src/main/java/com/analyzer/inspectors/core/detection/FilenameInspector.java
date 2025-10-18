package com.analyzer.inspectors.core.detection;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
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
@InspectorDependencies(produces = {
        InspectorTags.TAG_FILE_TYPE,
        FilenameInspector.TAGS.TAG_FILENAME_DETECTED + "*"

}) // No dependencies - fundamental detector
public class FilenameInspector implements Inspector<ProjectFile> {

    public static class TAGS {
        public static final String TAG_FILENAME_DETECTED = "filename.";

    }

    private final String name;
    private final String tag;
    private final Set<String> filenames;
    private final int priority;
    private final boolean caseSensitive;

    public FilenameInspector(String name, String tag, Collection<String> filenames) {
        this(name, tag, filenames, 0, false);
    }

    public FilenameInspector(String name, String tag, Collection<String> filenames, int priority,
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

    @Override
    public void decorate(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        // Set the detector tag using own TAGS constants
        projectFile.setTag(TAGS.TAG_FILENAME_DETECTED + getTag(), true);
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
    public static FilenameInspector createBuildFileInspector() {
        return new FilenameInspector(
                "Build Files",
                "build",
                Arrays.asList("pom.xml", "build.gradle", "build.xml", "Makefile", "CMakeLists.txt", "package.json"),
                15,
                false);
    }

    /**
     * Create an inspector for readme files
     */
    public static FilenameInspector createReadmeInspector() {
        return new FilenameInspector(
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
    public static FilenameInspector createDockerInspector() {
        return new FilenameInspector(
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
        FilenameInspector that = (FilenameInspector) o;
        return Objects.equals(name, that.name) && Objects.equals(filenames, that.filenames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, filenames);
    }

    @Override
    public String toString() {
        return "FilenameInspector{" +
                "name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", filenames=" + filenames +
                ", priority=" + priority +
                ", caseSensitive=" + caseSensitive +
                '}';
    }
}
