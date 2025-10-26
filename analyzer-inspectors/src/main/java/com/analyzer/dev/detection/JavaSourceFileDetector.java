package com.analyzer.dev.detection;

import com.analyzer.api.detector.FileDetector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.model.ClassType;
import com.analyzer.core.model.ProjectFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.analyzer.core.inspector.InspectorTags.*;

/**
 * Comprehensive inspector for Java source files (.java).
 * <p>
 * This is a foundational inspector that combines basic Java detection with
 * detailed metadata extraction. It performs both:
 * 1. Basic Java source file detection and tagging (formerly
 * IsJavaClassInspector functionality)
 * 2. Detailed package name, class name, and metadata extraction
 * <p>
 * Other Java-specific inspectors can depend on this inspector's output.
 * <p>
 * Merged from IsJavaClassInspector and JavaSourceFileDetector to eliminate
 * duplication.
 * <p>
 * Uses the new annotation-based dependency system - no dependencies as
 * foundational inspector.
 */
public class JavaSourceFileDetector implements FileDetector {

    private static final Logger logger = LoggerFactory.getLogger(JavaSourceFileDetector.class);

    // Using InspectorTags constants for consistency
    // Legacy TAGS class removed - use InspectorTags instead

    @Override
    public String getName() {
        return "Java Source File Inspector";
    }

    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null || projectFile.getFileName() == null) {
            return false;
        }

        return projectFile.hasFileExtension("java");
    }

    @Override
    public void detect(NodeDecorator<ProjectFile> decorator) {
        ProjectFile projectFile = decorator.getNode();

        try {
            // PHASE 1: Basic Java detection and fundamental tagging
            decorator.enableTag(TAG_JAVA_IS_SOURCE);
            decorator.setProperty(JAVA_FORMAT, FORMAT_SOURCE);
            decorator.setProperty(TAG_LANGUAGE, LANGUAGE_JAVA);
            decorator.enableTag(TAG_JAVA_DETECTED);

            // Mark this as having source code
            decorator.setProperty(TAG_JAVA_CLASS_TYPE, ClassType.SOURCE_ONLY.toString());


        } catch (Exception e) {
            logger.warn("Error processing Java source file {}: {}", projectFile.getFilePath(), e.getMessage());
            decorator.error("Processing error: " + e.getMessage());
        }
    }

}
