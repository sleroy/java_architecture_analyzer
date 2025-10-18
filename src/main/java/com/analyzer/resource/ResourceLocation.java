package com.analyzer.resource;
import com.analyzer.core.inspector.InspectorDependencies;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Represents a resource location using URI scheme.
 * Supports various schemes like file://, jar://, etc.
 */
public class ResourceLocation {
    private final URI uri;
    private final ResourceType type;

    public ResourceLocation(URI uri) {
        this.uri = Objects.requireNonNull(uri, "URI cannot be null");
        this.type = determineType(uri);
    }

    public ResourceLocation(String uriString) {
        try {
            this.uri = new URI(uriString);
            this.type = determineType(this.uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + uriString, e);
        }
    }

    /**
     * Create a file-based resource location.
     */
    public static ResourceLocation file(String path) {
        return new ResourceLocation("file://" + (path.startsWith("/") ? "" : "/") + path);
    }

    /**
     * Create a JAR-based resource location.
     */
    public static ResourceLocation jar(String jarPath, String entryPath) {
        String jarUri = jarPath.startsWith("file://") ? jarPath
                : "file://" + (jarPath.startsWith("/") ? "" : "/") + jarPath;
        return new ResourceLocation("jar:" + jarUri + "!/" + entryPath);
    }

    /**
     * Create a nested JAR resource location (e.g., WAR containing JAR).
     */
    public static ResourceLocation nestedJar(String outerPath, String innerPath, String entryPath) {
        String outerUri = outerPath.startsWith("file://") ? outerPath
                : "file://" + (outerPath.startsWith("/") ? "" : "/") + outerPath;
        return new ResourceLocation("jar:jar:" + outerUri + "!/" + innerPath + "!/" + entryPath);
    }

    public URI getUri() {
        return uri;
    }

    public ResourceType getType() {
        return type;
    }

    public String getScheme() {
        return uri.getScheme();
    }

    /**
     * Get the base path for file-based resources.
     */
    public String getFilePath() {
        if (type == ResourceType.FILE) {
            return uri.getPath();
        }
        throw new IllegalStateException("Not a file-based resource: " + uri);
    }

    /**
     * Get the JAR file path for JAR-based resources.
     */
    public String getJarPath() {
        if (type == ResourceType.JAR || type == ResourceType.NESTED_JAR) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            if (schemeSpecificPart.startsWith("file://")) {
                int exclamationIndex = schemeSpecificPart.indexOf('!');
                return exclamationIndex != -1 ? schemeSpecificPart.substring(7, exclamationIndex)
                        : schemeSpecificPart.substring(7);
            }
        }
        throw new IllegalStateException("Not a JAR-based resource: " + uri);
    }

    /**
     * Get the entry path within a JAR for JAR-based resources.
     */
    public String getEntryPath() {
        if (type == ResourceType.JAR || type == ResourceType.NESTED_JAR) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int exclamationIndex = schemeSpecificPart.lastIndexOf('!');
            if (exclamationIndex != -1 && exclamationIndex < schemeSpecificPart.length() - 1) {
                String entryPath = schemeSpecificPart.substring(exclamationIndex + 1);
                return entryPath.startsWith("/") ? entryPath.substring(1) : entryPath;
            }
        }
        throw new IllegalStateException("Not a JAR-based resource or no entry path: " + uri);
    }

    /**
     * Check if this resource represents a directory.
     */
    public boolean isDirectory() {
        return uri.getPath().endsWith("/");
    }

    /**
     * Get parent resource location.
     */
    public ResourceLocation getParent() {
        if (type == ResourceType.FILE) {
            String path = uri.getPath();
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                return new ResourceLocation(uri.resolve(path.substring(0, lastSlash + 1)));
            }
        }
        return null;
    }

    /**
     * Create a child resource location.
     */
    public ResourceLocation resolve(String relativePath) {
        if (type == ResourceType.FILE && isDirectory()) {
            return new ResourceLocation(uri.resolve(relativePath));
        }
        throw new IllegalStateException("Cannot resolve path on non-directory resource: " + uri);
    }

    private ResourceType determineType(URI uri) {
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            return ResourceType.FILE;
        } else if ("jar".equals(scheme)) {
            // Check for nested JAR (jar:jar:...)
            if (uri.getSchemeSpecificPart().startsWith("jar:")) {
                return ResourceType.NESTED_JAR;
            }
            return ResourceType.JAR;
        }
        return ResourceType.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ResourceLocation that = (ResourceLocation) o;
        return Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public String toString() {
        return "ResourceLocation{" +
                "uri=" + uri +
                ", type=" + type +
                '}';
    }

    /**
     * Types of resources supported.
     */
    public enum ResourceType {
        FILE, // file:// scheme
        JAR, // jar:// scheme
        NESTED_JAR, // jar:jar:// scheme for nested archives
        UNKNOWN // unsupported scheme
    }
}
