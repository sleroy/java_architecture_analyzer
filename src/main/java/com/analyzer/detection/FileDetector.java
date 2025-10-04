package com.analyzer.detection;

import com.analyzer.core.ProjectFile;
import java.nio.file.Path;

/**
 * Interface for detecting files that should be included in project analysis.
 * FileDetectors examine files and determine if they match certain criteria,
 * and assign appropriate tags when they do match.
 */
public interface FileDetector {

    /**
     * Get the name of this detector for identification purposes
     */
    String getName();

    /**
     * Get the tag that this detector assigns to matching files
     */
    String getTag();

    /**
     * Check if this detector matches the given file
     * 
     * @param filePath    The absolute path to the file
     * @param projectRoot The project root path for context
     * @return true if this detector matches the file
     */
    boolean matches(Path filePath, Path projectRoot);

    /**
     * Process a matching file and assign tags
     * This method is called after matches() returns true
     * 
     * @param projectFile The ProjectFile to process and tag
     */
    void processFile(ProjectFile projectFile);

    /**
     * Get the priority of this detector (higher = more important)
     * Used when multiple detectors match the same file
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Get a description of what this detector does
     */
    default String getDescription() {
        return "File detector: " + getName();
    }
}
