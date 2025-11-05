package com.analyzer.core.resource;

import com.analyzer.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ResourceResolver implementation for file:// scheme URIs.
 * Handles local file system access using NIO.2 APIs.
 */
public class FileResourceResolver implements ResourceResolver {

    private static final String FILE_SCHEME = "file";

    @Override
    public boolean supports(ResourceLocation location) {
        return FILE_SCHEME.equals(location.getScheme());
    }

    @Override
    public InputStream openStream(ResourceLocation location) throws IOException {
        validateLocation(location);
        Path path = getPath(location);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }

        if (Files.isDirectory(path)) {
            throw new IOException("Cannot open stream for directory: " + path);
        }

        return Files.newInputStream(path);
    }

    @Override
    public boolean exists(ResourceLocation location) {
        if (!supports(location)) {
            return false;
        }

        try {
            Path path = getPath(location);
            return Files.exists(path);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ResourceLocation> listChildren(ResourceLocation location) throws IOException {
        validateLocation(location);
        Path path = getPath(location);

        if (!Files.exists(path)) {
            throw new IOException("Directory not found: " + path);
        }

        if (!Files.isDirectory(path)) {
            throw new IOException("Not a directory: " + path);
        }

        try (Stream<Path> children = Files.list(path)) {
            return children
                    .map(childPath -> ResourceLocation.file(childPath.toString()))
                    .toList();
        }
    }

    @Override
    public ResourceMetadata getMetadata(ResourceLocation location) throws IOException {
        validateLocation(location);
        Path path = getPath(location);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }

        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        boolean isDirectory = Files.isDirectory(path);
        long size = isDirectory ? 0 : attrs.size();
        Instant lastModified = attrs.lastModifiedTime().toInstant();
        String contentType = determineContentType(path, isDirectory);

        return isDirectory
                ? ResourceMetadata.directory(lastModified)
                : ResourceMetadata.file(size, lastModified, contentType);
    }

    @Override
    public void close() throws IOException {
        // No resources to clean up for file system access
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
     * Converts ResourceLocation to NIO Path.
     */
    private Path getPath(ResourceLocation location) {
        return Paths.get(location.getUri());
    }

    /**
     * Determines content type based on file extension.
     */
    private String determineContentType(Path path, boolean isDirectory) {
        if (isDirectory) {
            return "application/x-directory";
        }

        String fileName = path.getFileName().toString().toLowerCase();

        // Java source files
        if (fileName.endsWith(".java")) {
            return "text/x-java-source";
        }

        // Java class files
        if (fileName.endsWith(".class")) {
            return "application/java-vm";
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

        // Text files
        if (fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                fileName.endsWith(".xml") || fileName.endsWith(".properties") ||
                fileName.endsWith(".json") || fileName.endsWith(".yaml") ||
                fileName.endsWith(".yml")) {
            return "text/plain";
        }

        // Default
        return "application/octet-stream";
    }
}
