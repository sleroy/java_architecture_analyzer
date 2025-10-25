package com.analyzer.inspectors.core.binary;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

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
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_DETECTED}, produces = { InspectorTags.TAG_JAVA_IS_BINARY })
public abstract class AbstractBinaryClassInspector implements Inspector<ProjectFile> {

    private final ResourceResolver resourceResolver;

    /**
     * Creates a new AbstractBinaryClassInspector with the specified
     * ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected AbstractBinaryClassInspector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public final void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator) {

        try {
            // For ProjectFile, create ResourceLocation from the file path
            ResourceLocation binaryLocation = new ResourceLocation(projectFile.getFilePath().toUri());

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
     */
    private void analyzeBinaryClass(ProjectFile projectFile, ResourceLocation binaryLocation,
            NodeDecorator<ProjectFile> decorator) {

        // Validate binary location before attempting to open stream
        if (binaryLocation == null) {
            decorator.error(
                    "Binary location is null for project file: " + projectFile.getFilePath());
            return;
        }

        try (InputStream classStream = resourceResolver.openStream(binaryLocation)) {
            if (classStream == null) {
                decorator.error(
                        "Could not open binary class stream: " + binaryLocation);
                return;
            }

            // Additional validation: check if stream is immediately at end
            if (!classStream.markSupported()) {
                // If mark not supported, we can't pre-check, so proceed to analyzeClassFile
                // which will handle empty streams
                analyzeClassFile(projectFile, binaryLocation, classStream, decorator);
                return;
            }

            // Mark current position and try to read one byte to check if stream has content
            classStream.mark(1);
            int firstByte = classStream.read();
            classStream.reset();

            if (firstByte == -1) {
                decorator.error(
                        "Empty class file stream: " + binaryLocation);
                return;
            }
            decorator.enableTag(InspectorTags.TAG_JAVA_IS_BINARY);
            analyzeClassFile(projectFile, binaryLocation, classStream, decorator);

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
