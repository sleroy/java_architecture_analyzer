package com.analyzer.dev.detection;

import com.analyzer.api.detector.FileDetector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;

import java.util.*;

/**
 * Inspector that detects files based on their file extensions and assigns
 * appropriate tags.
 * Configurable with a list of extensions to match.
 * This replaces the old FileExtensionDetector with Inspector pattern.
 *
 * <p>
 * As a fundamental detector, this inspector has no dependencies and operates
 * directly on file names and paths.
 * </p>
 */// No dependencies - fundamental detector
public class FileExtensionDetector implements FileDetector {

    public static class TAGS {
        public static final String TAG_DETECTOR_PREFIX = "detector.";
        public static final String TAG_EXTENSION_PREFIX = "extension.";
    }

    private final String name;
    private final String tag;
    private final Set<String> extensions;
    private final int priority;

    public FileExtensionDetector(String name, String tag, Collection<String> extensions) {
        this(name, tag, extensions, 0);
    }

    public FileExtensionDetector(String name, String tag, Collection<String> extensions, int priority) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.tag = Objects.requireNonNull(tag, "Tag cannot be null");
        this.extensions = new HashSet<>();
        this.priority = priority;

        // Normalize extensions (remove dots, convert to lowercase)
        if (extensions != null) {
            for (String ext : extensions) {
                if (ext != null && !ext.trim().isEmpty()) {
                    String normalized = ext.trim().toLowerCase();
                    if (normalized.startsWith(".")) {
                        normalized = normalized.substring(1);
                    }
                    this.extensions.add(normalized);
                }
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null) {
            return false;
        }

        String fileName = projectFile.getFileName();
        if (fileName == null) {
            return false;
        }

        String fileExtension = projectFile.getFileExtension();
        return extensions.contains(fileExtension);
    }

    @Override
    public void detect(NodeDecorator<ProjectFile> decorator) {
        // Set the detector tag using own TAGS constants
        decorator.enableTag(TAGS.TAG_DETECTOR_PREFIX + getTag());
        decorator.setProperty(InspectorTags.TAG_FILE_TYPE, getTag());

        // Add extension-specific processing
        String extension = decorator.getNode().getFileExtension();
        if (!extension.isEmpty()) {
            decorator.enableTag(TAGS.TAG_EXTENSION_PREFIX + extension);
        }
    }

    /**
     * Get the tag this inspector assigns to matching files
     */
    public String getTag() {
        return tag;
    }

    /**
     * Create a default Java file inspector
     */
    public static FileExtensionDetector createJavaInspector() {
        return new FileExtensionDetector(
                "Java Files",
                "java",
                List.of("java"),
                10);
    }

    /**
     * Create an inspector for XML files
     */
    public static FileExtensionDetector createXmlInspector() {
        return new FileExtensionDetector(
                "XML Files",
                "xml",
                List.of("xml"),
                8);
    }

    /**
     * Create an inspector for common configuration files
     */
    public static FileExtensionDetector createConfigInspector() {
        return new FileExtensionDetector(
                "Configuration Files",
                "config",
                Arrays.asList("properties", "yml", "yaml", "json", "conf"),
                5);
    }

    /**
     * Create an inspector for binary files
     */
    public static FileExtensionDetector createBinaryInspector() {
        return new FileExtensionDetector(
                "Binary Files",
                "binary",
                Arrays.asList("class", "jar", "war", "ear"),
                8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FileExtensionDetector that = (FileExtensionDetector) o;
        return Objects.equals(name, that.name) && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, extensions);
    }

    @Override
    public String toString() {
        return "FileExtensionDetector{" +
                "name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", extensions=" + extensions +
                ", priority=" + priority +
                '}';
    }
}
