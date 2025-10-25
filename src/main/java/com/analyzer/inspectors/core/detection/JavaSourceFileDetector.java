package com.analyzer.inspectors.core.detection;

import com.analyzer.core.detector.FileDetector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.model.ClassType;
import com.analyzer.core.model.ProjectFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // Pattern to extract package and class name from Java source files
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)\\s*;",
            Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record|@interface)\\s+(\\w+)",
            Pattern.MULTILINE);

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

            // PHASE 2: Detailed metadata extraction from file content
            String content = Files.readString(projectFile.getFilePath());

            // Extract package name
            String packageName = extractPackageName(content);
            if (packageName != null && !packageName.isEmpty()) {
                decorator.setProperty(TAG_JAVA_PACKAGE_NAME, packageName);
            }

            // Extract class name
            String className = extractClassName(content);
            if (className != null && !className.isEmpty()) {
                decorator.setProperty(TAG_JAVA_CLASS_NAME, className);
            }

            // Set the fully qualified name
            if (className != null) {
                String fullyQualifiedName = packageName != null && !packageName.isEmpty()
                        ? packageName + "." + className
                        : className;
                decorator.setProperty(TAG_JAVA_FULLY_QUALIFIED_NAME, fullyQualifiedName);
            }

            // Mark this as having source code
            decorator.setProperty(TAG_JAVA_CLASS_TYPE, ClassType.SOURCE_ONLY.toString());

            logger.debug("Processed Java source file: {} -> class: {}, package: {}",
                    projectFile.getFileName(), className, packageName);

        } catch (IOException e) {
            logger.warn("Failed to read Java source file {}: {}", projectFile.getFilePath(), e.getMessage());
            decorator.error("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Error processing Java source file {}: {}", projectFile.getFilePath(), e.getMessage());
            decorator.error("Processing error: " + e.getMessage());
        }
    }

    /**
     * Extract package name from Java source content.
     */
    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Extract the primary class name from Java source content.
     * Returns the first class/interface/enum found.
     */
    private String extractClassName(String content) {
        Matcher matcher = CLASS_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
}
