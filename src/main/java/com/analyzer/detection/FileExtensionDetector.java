package com.analyzer.detection;

import com.analyzer.core.ProjectFile;
import java.nio.file.Path;
import java.util.*;

/**
 * Detects files based on their file extensions.
 * Configurable with a list of extensions to match.
 */
public class FileExtensionDetector implements FileDetector {

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
    public String getTag() {
        return tag;
    }

    @Override
    public boolean matches(Path filePath, Path projectRoot) {
        if (filePath == null) {
            return false;
        }

        String fileName = filePath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');

        if (lastDot == -1) {
            // No extension
            return false;
        }

        String fileExtension = fileName.substring(lastDot + 1).toLowerCase();
        return extensions.contains(fileExtension);
    }

    @Override
    public void processFile(ProjectFile projectFile) {
        // Set the detector tag
        projectFile.setTag("detector." + getTag(), true);
        projectFile.setTag("fileType", getTag());

        // Add extension-specific processing
        String extension = projectFile.getFileExtension();
        if (!extension.isEmpty()) {
            projectFile.setTag("extension." + extension, true);
        }
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String getDescription() {
        return String.format("Extension detector '%s' for extensions: %s", name, extensions);
    }

    /**
     * Get the extensions this detector matches
     */
    public Set<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }

    /**
     * Create a default Java file detector
     */
    public static FileExtensionDetector createJavaDetector() {
        return new FileExtensionDetector(
                "Java Files",
                "java",
                Arrays.asList("java"),
                10);
    }

    /**
     * Create a detector for common configuration files
     */
    public static FileExtensionDetector createConfigDetector() {
        return new FileExtensionDetector(
                "Configuration Files",
                "config",
                Arrays.asList("properties", "yml", "yaml", "xml", "json", "conf"),
                5);
    }

    /**
     * Create a detector for binary files
     */
    public static FileExtensionDetector createBinaryDetector() {
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
