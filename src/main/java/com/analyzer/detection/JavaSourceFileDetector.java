package com.analyzer.detection;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.ClassType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FileDetector for Java source files (.java).
 * Extracts package name, class name, and other Java-specific metadata.
 */
public class JavaSourceFileDetector implements FileDetector {

    private static final Logger logger = LoggerFactory.getLogger(JavaSourceFileDetector.class);

    // Pattern to extract package and class name from Java source files
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)\\s*;",
            Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record|@interface)\\s+(\\w+)",
            Pattern.MULTILINE);

    @Override
    public String getName() {
        return "JavaSourceFileDetector";
    }

    @Override
    public String getTag() {
        return "java_source";
    }

    @Override
    public boolean matches(Path filePath, Path projectRoot) {
        return filePath.toString().toLowerCase().endsWith(".java");
    }

    @Override
    public void processFile(ProjectFile projectFile) {
        try {
            // Read the Java source file content
            String content = Files.readString(projectFile.getFilePath());

            // Extract package name
            String packageName = extractPackageName(content);
            if (packageName != null && !packageName.isEmpty()) {
                projectFile.setTag("java.packageName", packageName);
            }

            // Extract class name (first public class found)
            String className = extractClassName(content);
            if (className != null && !className.isEmpty()) {
                projectFile.setTag("java.className", className);
            }

            // Set the fully qualified name
            if (className != null) {
                String fullyQualifiedName = packageName != null && !packageName.isEmpty()
                        ? packageName + "." + className
                        : className;
                projectFile.setTag("java.fullyQualifiedName", fullyQualifiedName);
            }

            // Mark this as having source code
            projectFile.setTag("resource.hasSource", true);
            projectFile.setTag("java.classType", ClassType.SOURCE_ONLY.toString());

            // Set basic file type tags
            projectFile.setTag("file.type", "java_source");
            projectFile.setTag("language", "java");

            logger.debug("Processed Java source file: {} -> class: {}, package: {}",
                    projectFile.getFileName(), className, packageName);

        } catch (IOException e) {
            logger.warn("Failed to read Java source file {}: {}", projectFile.getFilePath(), e.getMessage());
            projectFile.setTag("processing.error", "Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Error processing Java source file {}: {}", projectFile.getFilePath(), e.getMessage());
            projectFile.setTag("processing.error", "Processing error: " + e.getMessage());
        }
    }

    @Override
    public int getPriority() {
        return 100; // High priority for Java source files
    }

    @Override
    public String getDescription() {
        return "Detects and processes Java source files (.java), extracting package and class information";
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
