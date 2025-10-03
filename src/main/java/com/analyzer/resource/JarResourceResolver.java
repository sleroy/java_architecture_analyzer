package com.analyzer.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResourceResolver implementation for jar:// scheme URIs.
 * Handles JAR files, WAR files, and nested JAR access.
 */
public class JarResourceResolver implements ResourceResolver {

    private static final Logger logger = LoggerFactory.getLogger(JarResourceResolver.class);
    private static final String JAR_SCHEME = "jar";
    private static final String JAR_SEPARATOR = "!/";

    // Cache for opened JarFiles to avoid reopening the same JAR multiple times
    private final ConcurrentHashMap<String, JarFile> jarCache = new ConcurrentHashMap<>();

    @Override
    public boolean supports(ResourceLocation location) {
        return JAR_SCHEME.equals(location.getScheme());
    }

    @Override
    public InputStream openStream(ResourceLocation location) throws IOException {
        logger.debug("JarResourceResolver.openStream: location={}", location.getUri());
        validateLocation(location);

        JarFileInfo jarInfo = parseJarLocation(location);
        logger.debug("JarResourceResolver.openStream: jarPath={}, entryPath={}", jarInfo.jarPath, jarInfo.entryPath);

        JarFile jarFile = getOrOpenJarFile(jarInfo.jarPath);
        logger.debug("JarResourceResolver.openStream: jarFile opened successfully");

        JarEntry entry = jarFile.getJarEntry(jarInfo.entryPath);
        if (entry == null) {
            logger.debug("JarResourceResolver.openStream: Entry not found: {}", jarInfo.entryPath);
            throw new IOException("Entry not found in JAR: " + jarInfo.entryPath);
        }

        if (entry.isDirectory()) {
            logger.debug("JarResourceResolver.openStream: Entry is directory: {}", jarInfo.entryPath);
            throw new IOException("Cannot open stream for directory entry: " + jarInfo.entryPath);
        }

        logger.debug("JarResourceResolver.openStream: Successfully opening stream for {}", jarInfo.entryPath);
        return jarFile.getInputStream(entry);
    }

    @Override
    public boolean exists(ResourceLocation location) {
        if (!supports(location)) {
            return false;
        }

        try {
            JarFileInfo jarInfo = parseJarLocation(location);

            // If entryPath is empty, we're checking if the JAR file itself exists
            if (jarInfo.entryPath.isEmpty()) {
                Path jarPath = Paths.get(jarInfo.jarPath);
                return jarPath.toFile().exists();
            }

            // Otherwise, check if the entry exists within the JAR
            JarFile jarFile = getOrOpenJarFile(jarInfo.jarPath);
            return jarFile.getJarEntry(jarInfo.entryPath) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ResourceLocation> listChildren(ResourceLocation location) throws IOException {
        validateLocation(location);

        JarFileInfo jarInfo = parseJarLocation(location);
        JarFile jarFile = getOrOpenJarFile(jarInfo.jarPath);

        String entryPath = jarInfo.entryPath;
        if (!entryPath.isEmpty() && !entryPath.endsWith("/")) {
            entryPath += "/";
        }

        final String searchPath = entryPath;
        List<ResourceLocation> children = new ArrayList<>();

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(searchPath) && !name.equals(searchPath)) {
                String relativePath = name.substring(searchPath.length());
                // Only direct children (no nested directories)
                if (!relativePath.contains("/") ||
                        (relativePath.endsWith("/") && relativePath.indexOf("/") == relativePath.length() - 1)) {
                    children.add(ResourceLocation.jar(jarInfo.jarPath, name));
                }
            }
        }

        return children;
    }

    @Override
    public ResourceMetadata getMetadata(ResourceLocation location) throws IOException {
        validateLocation(location);

        JarFileInfo jarInfo = parseJarLocation(location);
        JarFile jarFile = getOrOpenJarFile(jarInfo.jarPath);

        JarEntry entry = jarFile.getJarEntry(jarInfo.entryPath);
        if (entry == null) {
            throw new IOException("Entry not found in JAR: " + jarInfo.entryPath);
        }

        boolean isDirectory = entry.isDirectory();
        long size = isDirectory ? 0 : entry.getSize();
        Instant lastModified = Instant.ofEpochMilli(entry.getTime());
        String contentType = determineContentType(jarInfo.entryPath, isDirectory);

        return isDirectory
                ? ResourceMetadata.directory(lastModified)
                : ResourceMetadata.file(size, lastModified, contentType);
    }

    @Override
    public void close() throws IOException {
        // Close all cached JAR files
        for (JarFile jarFile : jarCache.values()) {
            try {
                jarFile.close();
            } catch (IOException e) {
                // Log but don't fail the entire close operation
                logger.warn("Failed to close JAR file: {}", e.getMessage());
            }
        }
        jarCache.clear();
    }

    /**
     * Validates that the location is supported by this resolver.
     */
    private void validateLocation(ResourceLocation location) {
        Objects.requireNonNull(location, "ResourceLocation cannot be null");
        if (!supports(location)) {
            throw new IllegalArgumentException("Unsupported scheme: " + location.getScheme());
        }
    }

    /**
     * Opens or retrieves a cached JarFile.
     */
    private JarFile getOrOpenJarFile(String jarPath) throws IOException {
        return jarCache.computeIfAbsent(jarPath, path -> {
            try {
                return new JarFile(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open JAR file: " + path, e);
            }
        });
    }

    /**
     * Parses a JAR location to extract jar file path and entry path.
     */
    private JarFileInfo parseJarLocation(ResourceLocation location) {
        String uriString = location.getUri().toString();

        if (!uriString.startsWith("jar:file:")) {
            throw new IllegalArgumentException("Invalid JAR URI format: " + uriString);
        }

        // Remove "jar:" prefix
        uriString = uriString.substring(4);

        int separatorIndex = uriString.indexOf(JAR_SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid JAR URI, missing '!' separator: " + uriString);
        }

        String jarPath = uriString.substring(0, separatorIndex);
        String entryPath = uriString.substring(separatorIndex + JAR_SEPARATOR.length());

        // Convert file:// URI to actual file path
        try {
            Path path = Paths.get(URI.create(jarPath));
            jarPath = path.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JAR file path in URI: " + jarPath, e);
        }

        return new JarFileInfo(jarPath, entryPath);
    }

    /**
     * Determines content type based on entry name.
     */
    private String determineContentType(String entryPath, boolean isDirectory) {
        if (isDirectory) {
            return "application/x-directory";
        }

        String fileName = entryPath.toLowerCase();

        // Java class files
        if (fileName.endsWith(".class")) {
            return "application/java-vm";
        }

        // Java source files
        if (fileName.endsWith(".java")) {
            return "text/x-java-source";
        }

        // Archive files
        if (fileName.endsWith(".jar")) {
            return "application/java-archive";
        }
        if (fileName.endsWith(".war")) {
            return "application/x-webarchive";
        }
        if (fileName.endsWith(".zip")) {
            return "application/zip";
        }

        // Text/config files
        if (fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                fileName.endsWith(".xml") || fileName.endsWith(".properties") ||
                fileName.endsWith(".json") || fileName.endsWith(".yaml") ||
                fileName.endsWith(".yml") || fileName.endsWith(".manifest")) {
            return "text/plain";
        }

        // Default
        return "application/octet-stream";
    }

    /**
     * Helper class to hold JAR file information.
     */
    private static class JarFileInfo {
        final String jarPath;
        final String entryPath;

        JarFileInfo(String jarPath, String entryPath) {
            this.jarPath = jarPath;
            this.entryPath = entryPath;
        }
    }
}
