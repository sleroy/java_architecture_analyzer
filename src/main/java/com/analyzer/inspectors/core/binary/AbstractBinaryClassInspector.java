package com.analyzer.inspectors.core.binary;

import com.analyzer.core.export.ProjectFileDecorator;
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
@InspectorDependencies(
        requires = {},
        produces = {InspectorTags.TAG_JAVA_IS_BINARY})
public abstract class AbstractBinaryClassInspector implements Inspector<ProjectFile> {

    private final ResourceResolver resourceResolver;

    /**
     * Creates a new AbstractBinaryClassInspector with the specified ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected AbstractBinaryClassInspector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public final void decorate(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {

        try {
            // For ProjectFile, create ResourceLocation from the file path
            ResourceLocation binaryLocation = new ResourceLocation(projectFile.getFilePath().toUri());

            analyzeBinaryClass(projectFile, binaryLocation, projectFileDecorator);

        } catch (Exception e) {
            projectFileDecorator.error("Error analyzing binary class: " + e.getMessage());
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
    private void analyzeBinaryClass(ProjectFile projectFile, ResourceLocation binaryLocation, ProjectFileDecorator projectFileDecorator) {

        // Validate binary location before attempting to open stream
        if (binaryLocation == null) {
            projectFileDecorator.error(
                    "Binary location is null for project file: " + projectFile.getFilePath());
            return;
        }

        try (InputStream classStream = resourceResolver.openStream(binaryLocation)) {
            if (classStream == null) {
                projectFileDecorator.error(
                        "Could not open binary class stream: " + binaryLocation);
                return;
            }

            // Additional validation: check if stream is immediately at end
            if (!classStream.markSupported()) {
                // If mark not supported, we can't pre-check, so proceed to analyzeClassFile
                // which will handle empty streams
                analyzeClassFile(projectFile, binaryLocation, classStream, projectFileDecorator);
                return;
            }

            // Mark current position and try to read one byte to check if stream has content
            classStream.mark(1);
            int firstByte = classStream.read();
            classStream.reset();

            if (firstByte == -1) {
                projectFileDecorator.error(
                        "Empty class file stream: " + binaryLocation);
                return;
            }
            projectFileDecorator.setTag(InspectorTags.TAG_JAVA_IS_BINARY, true);
            analyzeClassFile(projectFile, binaryLocation, classStream, projectFileDecorator);

        } catch (IOException e) {
            projectFileDecorator.error(
                    "Error accessing binary class file: " + binaryLocation + " - " + e.getMessage());
        } catch (Exception e) {
            projectFileDecorator.error(
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
     * @throws IOException if there's an error reading the class file
     */
    protected abstract void analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
                                             InputStream classInputStream, ProjectFileDecorator projectFileDecorator) throws IOException;
}
