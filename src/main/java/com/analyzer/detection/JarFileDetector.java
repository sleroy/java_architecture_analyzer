package com.analyzer.detection;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.ClassType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * FileDetector for JAR and WAR files.
 * Scans the JAR contents and catalogs all .class files found inside,
 * tagging the JAR file with metadata about its contents.
 */
public class JarFileDetector implements FileDetector {

    private static final Logger logger = LoggerFactory.getLogger(JarFileDetector.class);

    @Override
    public String getName() {
        return "JarFileDetector";
    }

    @Override
    public String getTag() {
        return "java_archive";
    }

    @Override
    public boolean matches(Path filePath, Path projectRoot) {
        String fileName = filePath.toString().toLowerCase();
        return fileName.endsWith(".jar") || fileName.endsWith(".war") || fileName.endsWith(".ear");
    }

    @Override
    public void processFile(ProjectFile projectFile) {
        try {
            List<String> classNames = new ArrayList<>();
            List<String> packages = new ArrayList<>();
            Set<String> uniquePackages = new HashSet<>();
            int totalClassFiles = 0;
            int totalFiles = 0;

            // Scan the JAR file contents
            try (JarFile jarFile = new JarFile(projectFile.getFilePath().toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    totalFiles++;

                    if (entry.isDirectory()) {
                        continue;
                    }

                    String entryName = entry.getName();

                    // Process .class files
                    if (entryName.endsWith(".class")) {
                        totalClassFiles++;

                        // Convert path to fully qualified class name
                        String className = entryName.replace('/', '.').replace(".class", "");

                        // Skip anonymous and inner classes for the summary
                        if (!className.contains("$")) {
                            classNames.add(className);

                            // Extract package name
                            int lastDot = className.lastIndexOf('.');
                            if (lastDot > 0) {
                                String packageName = className.substring(0, lastDot);
                                uniquePackages.add(packageName);
                            }
                        }
                    }
                }
            }

            packages.addAll(uniquePackages);
            Collections.sort(packages);
            Collections.sort(classNames);

            // Tag the JAR file with metadata about its contents
            projectFile.setTag("archive.type", detectArchiveType(projectFile.getFileName()));
            projectFile.setTag("archive.totalFiles", totalFiles);
            projectFile.setTag("archive.totalClassFiles", totalClassFiles);
            projectFile.setTag("archive.classCount", classNames.size());
            projectFile.setTag("archive.packageCount", packages.size());

            // Store lists of contents (limit size to prevent memory issues)
            if (classNames.size() <= 1000) {
                projectFile.setTag("archive.classNames", classNames);
            } else {
                // For very large JARs, just store a sample
                projectFile.setTag("archive.classNames", classNames.subList(0, 1000));
                projectFile.setTag("archive.classNamesTruncated", true);
            }

            if (packages.size() <= 200) {
                projectFile.setTag("archive.packages", packages);
            } else {
                projectFile.setTag("archive.packages", packages.subList(0, 200));
                projectFile.setTag("archive.packagesTruncated", true);
            }

            // Mark this as having binary code
            projectFile.setTag("resource.hasBinary", true);
            projectFile.setTag("java.classType", ClassType.BINARY_ONLY.toString());

            // Set basic file type tags
            projectFile.setTag("file.type", "java_archive");
            projectFile.setTag("language", "java");

            // Set archive-specific metadata
            projectFile.setTag("archive.isLibrary", isLikelyLibrary(classNames, packages));
            projectFile.setTag("archive.isApplication", isLikelyApplication(classNames));

            logger.debug("Processed JAR file: {} -> {} classes in {} packages",
                    projectFile.getFileName(), classNames.size(), packages.size());

        } catch (IOException e) {
            logger.warn("Failed to read JAR file {}: {}", projectFile.getFilePath(), e.getMessage());
            projectFile.setTag("processing.error", "Failed to read JAR: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Error processing JAR file {}: {}", projectFile.getFilePath(), e.getMessage());
            projectFile.setTag("processing.error", "Processing error: " + e.getMessage());
        }
    }

    @Override
    public int getPriority() {
        return 80; // High priority for archives
    }

    @Override
    public String getDescription() {
        return "Detects and processes Java archive files (.jar, .war, .ear), cataloging contained classes and packages";
    }

    /**
     * Detect the type of archive based on file extension and contents
     */
    private String detectArchiveType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".war")) {
            return "WAR";
        } else if (lowerName.endsWith(".ear")) {
            return "EAR";
        } else if (lowerName.endsWith(".jar")) {
            return "JAR";
        }
        return "UNKNOWN";
    }

    /**
     * Heuristic to determine if this is likely a library JAR
     */
    private boolean isLikelyLibrary(List<String> classNames, List<String> packages) {
        if (classNames.isEmpty()) {
            return false;
        }

        // Libraries typically have:
        // - No main classes
        // - Consistent package structure
        // - Utility/framework-style class names

        boolean hasMainClass = classNames.stream()
                .anyMatch(name -> name.contains("Main") || name.contains("Application") || name.contains("App"));

        // If it has consistent package structure (most classes in same root package)
        boolean hasConsistentPackaging = false;
        if (!packages.isEmpty()) {
            String mostCommonRoot = findMostCommonPackageRoot(packages);
            long classesInCommonRoot = classNames.stream()
                    .filter(className -> className.startsWith(mostCommonRoot))
                    .count();
            hasConsistentPackaging = (double) classesInCommonRoot / classNames.size() > 0.6;
        }

        return !hasMainClass && hasConsistentPackaging;
    }

    /**
     * Heuristic to determine if this is likely an application JAR
     */
    private boolean isLikelyApplication(List<String> classNames) {
        if (classNames.isEmpty()) {
            return false;
        }

        // Applications typically have main classes or servlet classes
        return classNames.stream()
                .anyMatch(name -> name.contains("Main") ||
                        name.contains("Application") ||
                        name.contains("App") ||
                        name.contains("Servlet") ||
                        name.contains("Controller"));
    }

    /**
     * Find the most common package root among all packages
     */
    private String findMostCommonPackageRoot(List<String> packages) {
        if (packages.isEmpty()) {
            return "";
        }

        Map<String, Integer> rootCounts = new HashMap<>();

        for (String pkg : packages) {
            String[] parts = pkg.split("\\.");
            if (parts.length > 0) {
                String root = parts[0];
                if (parts.length > 1) {
                    root = parts[0] + "." + parts[1]; // Use first two levels
                }
                rootCounts.merge(root, 1, Integer::sum);
            }
        }

        return rootCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }
}
