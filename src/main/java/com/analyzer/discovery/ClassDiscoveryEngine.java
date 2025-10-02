package com.analyzer.discovery;

import com.analyzer.core.Clazz;
import com.analyzer.core.Clazz.ClassType;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import com.analyzer.resource.ResourceMetadata;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine for discovering Java classes from source files (.java) and binary
 * files (.class in JARs/WARs) using URI-based ResourceResolver system.
 * Handles large codebases efficiently with streaming processing and memory
 * optimization.
 */
public class ClassDiscoveryEngine {

    // Pattern to extract package and class name from Java source files
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)\\s*;",
            Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:abstract\\s+)?(?:final\\s+)?(?:class|interface|enum|record|@interface)\\s+(\\w+)",
            Pattern.MULTILINE);

    private final ResourceResolver resourceResolver;
    private final Set<String> processedClasses;
    private final Map<String, Clazz> discoveredClasses;

    public ClassDiscoveryEngine(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.processedClasses = ConcurrentHashMap.newKeySet();
        this.discoveredClasses = new ConcurrentHashMap<>();
    }

    /**
     * Discover all classes from the given source and binary resource locations.
     * 
     * @param sourceLocation  ResourceLocation for source files (can be null)
     * @param binaryLocations List of ResourceLocations for JAR/WAR files (can be
     *                        null or empty)
     * @return Map of fully qualified class names to Clazz objects
     * @throws IOException if resource scanning fails
     */
    public Map<String, Clazz> discoverClasses(ResourceLocation sourceLocation, List<ResourceLocation> binaryLocations)
            throws IOException {
        // Clear previous results
        processedClasses.clear();
        discoveredClasses.clear();

        // Discover from source files first
        if (sourceLocation != null && resourceResolver.exists(sourceLocation)) {
            discoverFromSource(sourceLocation);
        }

        // Discover from binary files
        if (binaryLocations != null) {
            for (ResourceLocation binaryLocation : binaryLocations) {
                if (resourceResolver.exists(binaryLocation)) {
                    if (binaryLocation.getType() == ResourceLocation.ResourceType.JAR) {
                        discoverFromJar(binaryLocation);
                    } else if (binaryLocation.getType() == ResourceLocation.ResourceType.NESTED_JAR) {
                        discoverFromNestedJar(binaryLocation);
                    }
                }
            }
        }

        return new HashMap<>(discoveredClasses);
    }

    /**
     * Discover classes from source directory by scanning .java files.
     */
    private void discoverFromSource(ResourceLocation sourceLocation) throws IOException {
        ResourceMetadata metadata = resourceResolver.getMetadata(sourceLocation);

        if (metadata.isDirectory()) {
            // List all children and filter for .java files
            List<ResourceLocation> children = new ArrayList<>(resourceResolver.listChildren(sourceLocation));
            for (ResourceLocation child : children) {
                ResourceMetadata childMetadata = resourceResolver.getMetadata(child);

                if (childMetadata.isDirectory()) {
                    // Recursively process subdirectories
                    discoverFromSource(child);
                } else if (child.getUri().toString().endsWith(".java")) {
                    processSourceFile(child);
                }
            }
        } else if (sourceLocation.getUri().toString().endsWith(".java")) {
            // Single Java file
            processSourceFile(sourceLocation);
        }
    }

    /**
     * Process a single Java source file to extract class information.
     */
    private void processSourceFile(ResourceLocation javaFile) throws IOException {
        String content = readResourceContent(javaFile);

        // Extract package name
        String packageName = extractPackageName(content);

        // Extract class names (can be multiple in one file - inner classes, etc.)
        List<String> classNames = extractClassNames(content);

        for (String className : classNames) {
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            Clazz existingClass = discoveredClasses.get(fullyQualifiedName);
            if (existingClass != null) {
                // Class already exists (from binary), create new instance with BOTH type
                Clazz updatedClass = new Clazz(className, packageName, ClassType.BOTH,
                        javaFile, existingClass.getBinaryLocation());
                // Copy over any existing inspector results
                updatedClass.getInspectorResults().putAll(existingClass.getInspectorResults());
                discoveredClasses.put(fullyQualifiedName, updatedClass);
            } else {
                // New class from source
                Clazz clazz = new Clazz(className, packageName, ClassType.SOURCE_ONLY,
                        javaFile, null);
                discoveredClasses.put(fullyQualifiedName, clazz);
            }
            processedClasses.add(fullyQualifiedName);
        }
    }

    /**
     * Discover classes from JAR file by scanning .class entries.
     */
    private void discoverFromJar(ResourceLocation jarLocation) throws IOException {
        // List all children in the JAR
        List<ResourceLocation> children = new ArrayList<>(resourceResolver.listChildren(jarLocation));

        for (ResourceLocation child : children) {
            if (child.getUri().toString().endsWith(".class")) {
                processJarClassEntry(child, jarLocation);
            } else {
                // Check if it's a directory, and recursively scan it
                ResourceMetadata metadata = resourceResolver.getMetadata(child);
                if (metadata.isDirectory()) {
                    discoverFromJarDirectory(child, jarLocation);
                }
            }
        }
    }

    /**
     * Recursively discover classes from a directory within a JAR.
     */
    private void discoverFromJarDirectory(ResourceLocation directoryLocation, ResourceLocation jarLocation)
            throws IOException {
        List<ResourceLocation> children = new ArrayList<>(resourceResolver.listChildren(directoryLocation));

        for (ResourceLocation child : children) {
            if (child.getUri().toString().endsWith(".class")) {
                processJarClassEntry(child, jarLocation);
            } else {
                ResourceMetadata metadata = resourceResolver.getMetadata(child);
                if (metadata.isDirectory()) {
                    discoverFromJarDirectory(child, jarLocation);
                }
            }
        }
    }

    /**
     * Discover classes from nested JAR (like WAR files).
     */
    private void discoverFromNestedJar(ResourceLocation nestedJarLocation) throws IOException {
        // For nested JARs, treat similar to regular JARs
        discoverFromJar(nestedJarLocation);
    }

    /**
     * Process a .class entry from a JAR to extract class information.
     */
    private void processJarClassEntry(ResourceLocation classLocation, ResourceLocation jarLocation) {
        String classPath = extractClassPath(classLocation);

        // Convert path to fully qualified class name
        String fullyQualifiedName = classPath.replace('/', '.').replace(".class", "");

        // Skip anonymous classes and inner classes for now (can be enhanced later)
        if (fullyQualifiedName.contains("$")) {
            return;
        }

        // Extract package and class name
        String packageName = "";
        String className = fullyQualifiedName;

        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot != -1) {
            packageName = fullyQualifiedName.substring(0, lastDot);
            className = fullyQualifiedName.substring(lastDot + 1);
        }

        Clazz existingClass = discoveredClasses.get(fullyQualifiedName);
        if (existingClass != null) {
            // Class already exists (from source), create new instance with BOTH type
            Clazz updatedClass = new Clazz(className, packageName, ClassType.BOTH,
                    existingClass.getSourceLocation(), classLocation);
            // Copy over any existing inspector results
            updatedClass.getInspectorResults().putAll(existingClass.getInspectorResults());
            discoveredClasses.put(fullyQualifiedName, updatedClass);
        } else {
            // New class from binary
            Clazz clazz = new Clazz(className, packageName, ClassType.BINARY_ONLY,
                    null, classLocation);
            discoveredClasses.put(fullyQualifiedName, clazz);
        }
        processedClasses.add(fullyQualifiedName);
    }

    /**
     * Extract class path from ResourceLocation URI.
     * Handles different URI schemes to extract the actual class path.
     */
    private String extractClassPath(ResourceLocation classLocation) {
        String uri = classLocation.getUri().toString();

        // For jar:// schemes, extract the part after the JAR file
        if (uri.startsWith("jar://")) {
            int exclamationIndex = uri.indexOf('!');
            if (exclamationIndex != -1) {
                return uri.substring(exclamationIndex + 2); // Skip "!/"
            }
        }

        // For file:// schemes, extract the filename
        if (uri.startsWith("file://")) {
            int lastSlash = uri.lastIndexOf('/');
            if (lastSlash != -1) {
                return uri.substring(lastSlash + 1);
            }
        }

        // Fallback - try to extract from entry path for JAR resources
        try {
            if (classLocation.getType() == ResourceLocation.ResourceType.JAR ||
                    classLocation.getType() == ResourceLocation.ResourceType.NESTED_JAR) {
                return classLocation.getEntryPath();
            } else if (classLocation.getType() == ResourceLocation.ResourceType.FILE) {
                String filePath = classLocation.getFilePath();
                int lastSlash = filePath.lastIndexOf('/');
                return lastSlash != -1 ? filePath.substring(lastSlash + 1) : filePath;
            }
        } catch (Exception e) {
            // If all else fails, extract from the URI string
            String uriStr = uri;
            int lastSlash = uriStr.lastIndexOf('/');
            return lastSlash != -1 ? uriStr.substring(lastSlash + 1) : uriStr;
        }

        // Final fallback
        return uri;
    }

    /**
     * Read resource content using ResourceResolver.
     */
    private String readResourceContent(ResourceLocation location) throws IOException {
        try (InputStream inputStream = resourceResolver.openStream(location)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
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
     * Extract class names from Java source content.
     * Note: This is a simplified implementation. A full parser would be more
     * accurate.
     */
    private List<String> extractClassNames(String content) {
        List<String> classNames = new ArrayList<>();
        Matcher matcher = CLASS_PATTERN.matcher(content);

        while (matcher.find()) {
            classNames.add(matcher.group(1));
        }

        return classNames.isEmpty() ? Collections.singletonList("UnknownClass") : classNames;
    }

    /**
     * Get statistics about the discovery process.
     */
    public DiscoveryStatistics getStatistics() {
        long sourceOnly = discoveredClasses.values().stream()
                .mapToLong(clazz -> clazz.getClassType() == ClassType.SOURCE_ONLY ? 1 : 0)
                .sum();

        long binaryOnly = discoveredClasses.values().stream()
                .mapToLong(clazz -> clazz.getClassType() == ClassType.BINARY_ONLY ? 1 : 0)
                .sum();

        long both = discoveredClasses.values().stream()
                .mapToLong(clazz -> clazz.getClassType() == ClassType.BOTH ? 1 : 0)
                .sum();

        return new DiscoveryStatistics(sourceOnly, binaryOnly, both);
    }

    /**
     * Statistics about the discovery process.
     */
    public static class DiscoveryStatistics {
        private final long sourceOnlyClasses;
        private final long binaryOnlyClasses;
        private final long bothClasses;

        public DiscoveryStatistics(long sourceOnlyClasses, long binaryOnlyClasses, long bothClasses) {
            this.sourceOnlyClasses = sourceOnlyClasses;
            this.binaryOnlyClasses = binaryOnlyClasses;
            this.bothClasses = bothClasses;
        }

        public long getSourceOnlyClasses() {
            return sourceOnlyClasses;
        }

        public long getBinaryOnlyClasses() {
            return binaryOnlyClasses;
        }

        public long getBothClasses() {
            return bothClasses;
        }

        public long getTotalClasses() {
            return sourceOnlyClasses + binaryOnlyClasses + bothClasses;
        }

        @Override
        public String toString() {
            return String.format("Discovery Statistics: Total=%d, Source-only=%d, Binary-only=%d, Both=%d",
                    getTotalClasses(), sourceOnlyClasses, binaryOnlyClasses, bothClasses);
        }
    }
}
