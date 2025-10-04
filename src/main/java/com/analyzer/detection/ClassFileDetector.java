package com.analyzer.detection;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.ClassType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * FileDetector for compiled Java class files (.class).
 * Extracts package and class information from the file path structure.
 */
public class ClassFileDetector implements FileDetector {

    private static final Logger logger = LoggerFactory.getLogger(ClassFileDetector.class);

    @Override
    public String getName() {
        return "ClassFileDetector";
    }

    @Override
    public String getTag() {
        return "java_class";
    }

    @Override
    public boolean matches(Path filePath, Path projectRoot) {
        return filePath.toString().toLowerCase().endsWith(".class");
    }

    @Override
    public void processFile(ProjectFile projectFile) {
        try {
            // Extract class information from the file path
            Path filePath = projectFile.getFilePath();
            String fileName = projectFile.getFileName();

            // Remove .class extension to get class name
            String className = fileName.substring(0, fileName.length() - 6); // Remove ".class"

            // Skip anonymous and inner classes
            if (className.contains("$")) {
                logger.debug("Skipping inner/anonymous class: {}", className);
                projectFile.setTag("java.classType", "inner_or_anonymous");
                projectFile.setTag("file.type", "java_class_inner");
                return;
            }

            // Extract package name from directory structure
            String packageName = extractPackageFromPath(filePath);

            // Set Java-specific tags
            projectFile.setTag("java.className", className);
            if (packageName != null && !packageName.isEmpty()) {
                projectFile.setTag("java.packageName", packageName);
                projectFile.setTag("java.fullyQualifiedName", packageName + "." + className);
            } else {
                projectFile.setTag("java.fullyQualifiedName", className);
            }

            // Mark this as having binary code
            projectFile.setTag("resource.hasBinary", true);
            projectFile.setTag("java.classType", ClassType.BINARY_ONLY.toString());

            // Set basic file type tags
            projectFile.setTag("file.type", "java_class");
            projectFile.setTag("language", "java");

            logger.debug("Processed class file: {} -> class: {}, package: {}",
                    projectFile.getFileName(), className, packageName);

        } catch (Exception e) {
            logger.warn("Error processing class file {}: {}", projectFile.getFilePath(), e.getMessage());
            projectFile.setTag("processing.error", "Processing error: " + e.getMessage());
        }
    }

    @Override
    public int getPriority() {
        return 90; // High priority, but slightly lower than source files
    }

    @Override
    public String getDescription() {
        return "Detects and processes compiled Java class files (.class), extracting package and class information from file path";
    }

    /**
     * Extract package name from the file path structure.
     * Looks for common Java directory structures like src/main/java, build/classes,
     * target/classes, etc.
     */
    private String extractPackageFromPath(Path filePath) {
        String pathStr = filePath.toString().replace('\\', '/');

        // Common Java build output directories
        String[] commonRoots = {
                "/target/classes/",
                "/build/classes/",
                "/out/production/",
                "/bin/",
                "/classes/",
                "/src/main/java/",
                "/src/java/",
                "/java/"
        };

        // Find the package path after a known root
        for (String root : commonRoots) {
            int rootIndex = pathStr.lastIndexOf(root);
            if (rootIndex != -1) {
                String packagePath = pathStr.substring(rootIndex + root.length());
                // Remove the class filename from the end
                int lastSlash = packagePath.lastIndexOf('/');
                if (lastSlash > 0) {
                    packagePath = packagePath.substring(0, lastSlash);
                    return packagePath.replace('/', '.');
                }
                break;
            }
        }

        // Fallback: try to extract from parent directories
        Path parent = filePath.getParent();
        if (parent != null) {
            // Look for a reasonable package structure (directories with lowercase names)
            StringBuilder packageBuilder = new StringBuilder();
            Path current = parent;

            // Go up the tree and collect package components
            while (current != null && current.getFileName() != null) {
                String dirName = current.getFileName().toString();

                // Stop at common root directories
                if (dirName.equals("classes") || dirName.equals("java") ||
                        dirName.equals("main") || dirName.equals("src") ||
                        dirName.equals("target") || dirName.equals("build")) {
                    break;
                }

                // Add to package path if it looks like a package component
                if (isValidPackageComponent(dirName)) {
                    if (packageBuilder.length() > 0) {
                        packageBuilder.insert(0, ".");
                    }
                    packageBuilder.insert(0, dirName);
                }

                current = current.getParent();
            }

            return packageBuilder.toString();
        }

        return ""; // Default package
    }

    /**
     * Check if a directory name looks like a valid Java package component
     */
    private boolean isValidPackageComponent(String dirName) {
        if (dirName == null || dirName.isEmpty()) {
            return false;
        }

        // Should start with lowercase letter and contain only valid package characters
        return dirName.matches("[a-z][a-zA-Z0-9_]*");
    }
}
