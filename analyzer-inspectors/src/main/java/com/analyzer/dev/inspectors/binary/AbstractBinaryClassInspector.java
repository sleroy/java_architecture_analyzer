package com.analyzer.dev.inspectors.binary;

import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.api.resource.ResourceResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for all binary class inspectors.
 * Provides common functionality for analyzing compiled Java class files.
 * Uses ResourceResolver for unified access to class files in various locations.
 *
 * <p>
 * This class automatically requires JAVA_IS_BINARY and JAVA_DETECTED tags,
 * ensuring that binary inspectors only run on files that have been properly
 * identified as Java binary class files.
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED }, produces = { InspectorTags.TAG_JAVA_IS_BINARY })
public abstract class AbstractBinaryClassInspector implements Inspector<ProjectFile> {

    private final ResourceResolver resourceResolver;
    protected final LocalCache localCache;

    /**
     * Creates a new AbstractBinaryClassInspector with the specified
     * ResourceResolver and LocalCache.
     *
     * @param resourceResolver the resolver for accessing class file resources
     * @param localCache       per-item cache for optimizing binary file access
     */
    protected AbstractBinaryClassInspector(ResourceResolver resourceResolver, LocalCache localCache) {
        this.resourceResolver = resourceResolver;
        this.localCache = localCache;
    }

    @Override
    public final void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator) {

        try {
            // Use LocalCache to avoid repeated ResourceLocation creation
            ResourceLocation binaryLocation = (ResourceLocation) localCache
                    .getOrResolveLocation(() -> new ResourceLocation(projectFile.getFilePath().toUri()));

            analyzeBinaryClass(projectFile, binaryLocation, decorator);

        } catch (Exception e) {
            decorator.error("Error analyzing binary class: " + e.getMessage());
        }
    }

    public boolean supports(ProjectFile projectFile) {
        // Focus on file format validation only - dependencies are handled by
        // canProcess()
        return projectFile != null &&
                (projectFile.hasFileExtension("class") ||
                        projectFile.getFilePath().toString().endsWith(".class"));
    }

    /**
     * Analyzes a binary class using the ResourceResolver.
     * Uses LocalCache to avoid re-reading the same binary file multiple times.
     */
    private void analyzeBinaryClass(ProjectFile projectFile, ResourceLocation binaryLocation,
            NodeDecorator<ProjectFile> decorator) {

        // Validate binary location before attempting to open stream
        if (binaryLocation == null) {
            decorator.error(
                    "Binary location is null for project file: " + projectFile.getFilePath());
            return;
        }

        try {
            // Use LocalCache to read binary bytes only once per item
            byte[] classBytes = localCache.getOrLoadClassBytes(() -> {
                try (InputStream classStream = resourceResolver.openStream(binaryLocation)) {
                    if (classStream == null) {
                        throw new RuntimeException("Could not open binary class stream: " + binaryLocation);
                    }
                    return classStream.readAllBytes();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read class bytes", e);
                }
            });

            // Check for empty class files
            if (classBytes.length == 0) {
                decorator.error("Empty class file stream: " + binaryLocation);
                return;
            }

            // Mark as binary class
            decorator.enableTag(InspectorTags.TAG_JAVA_IS_BINARY);

            // Create a new InputStream from cached bytes for analysis
            try (InputStream classStream = new ByteArrayInputStream(classBytes)) {
                analyzeClassFile(projectFile, binaryLocation, classStream, decorator);
            }

        } catch (IOException e) {
            decorator.error(
                    "Error accessing binary class file: " + binaryLocation + " - " + e.getMessage());
        } catch (Exception e) {
            decorator.error(
                    "Unexpected error analyzing binary class: " + binaryLocation + " - " + e.getMessage());
        }
    }

    /**
     * Analyzes the binary class file for the given class.
     * Subclasses must implement this method to provide specific analysis logic.
     *
     * @param projectFile      the project file to analyze
     * @param binaryLocation   the location of the binary class file
     * @param classInputStream the input stream to the class file
     * @param decorator        the decorator for setting properties and tags
     * @throws IOException if there's an error reading the class file
     */
    protected abstract void analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
            InputStream classInputStream, NodeDecorator<ProjectFile> decorator) throws IOException;
}
