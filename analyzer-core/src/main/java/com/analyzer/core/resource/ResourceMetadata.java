package com.analyzer.core.resource;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata information about a resource.
 */
public class ResourceMetadata {
    private final long size;
    private final Instant lastModified;
    private final boolean isDirectory;
    private final String contentType;

    public ResourceMetadata(long size, Instant lastModified, boolean isDirectory, String contentType) {
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
        this.contentType = contentType;
    }

    /**
     * Create metadata for a directory.
     */
    public static ResourceMetadata directory(Instant lastModified) {
        return new ResourceMetadata(-1, lastModified, true, "directory");
    }

    /**
     * Create metadata for a file.
     */
    public static ResourceMetadata file(long size, Instant lastModified, String contentType) {
        return new ResourceMetadata(size, lastModified, false, contentType);
    }

    /**
     * Create metadata for a JAR entry.
     */
    public static ResourceMetadata jarEntry(long size, Instant lastModified) {
        return new ResourceMetadata(size, lastModified, false, "application/java-class");
    }

    public long getSize() {
        return size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ResourceMetadata that = (ResourceMetadata) o;
        return size == that.size &&
                isDirectory == that.isDirectory &&
                Objects.equals(lastModified, that.lastModified) &&
                Objects.equals(contentType, that.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, lastModified, isDirectory, contentType);
    }

    @Override
    public String toString() {
        return "ResourceMetadata{" +
                "size=" + size +
                ", lastModified=" + lastModified +
                ", isDirectory=" + isDirectory +
                ", contentType='" + contentType + '\'' +
                '}';
    }
}
