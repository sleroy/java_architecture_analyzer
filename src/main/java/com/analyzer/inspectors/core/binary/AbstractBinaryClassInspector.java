package com.analyzer.inspectors.core.binary;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.core.InspectorTags;
import com.analyzer.core.RequiredTags;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for all binary class inspectors.
 * Provides common functionality for analyzing compiled Java class files.
 * Uses ResourceResolver for unified access to class files in various locations.
 */
public abstract class BinaryClassInspector implements Inspector<ProjectFile> {

    private final ResourceResolver resourceResolver;

    /**
     * Creates a new BinaryClassInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected BinaryClassInspector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public final InspectorResult decorate(ProjectFile projectFile) {
        if (!supports(projectFile)) {
            return InspectorResult.notApplicable(getColumnName());
        }

        // If the project file doesn't actually have binary code, return not applicable
        if (!projectFile.getBooleanTag(InspectorTags.JAVA_IS_BINARY, false)
                && !projectFile.getFilePath().toString().endsWith(".class")) {
            return InspectorResult.notApplicable(getColumnName());
        }

        try {
            // For ProjectFile, create ResourceLocation from the file path
            ResourceLocation binaryLocation = new ResourceLocation(projectFile.getFilePath().toUri());

            return analyzeBinaryClass(projectFile, binaryLocation);

        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "Error analyzing binary class: " + e.getMessage());
        }
    }

    @Override
    public void depends(RequiredTags tags) {
        // Binary class inspectors require files that have been tagged with binary
        // availability
        tags.requires(InspectorTags.JAVA_IS_BINARY)
                .requires(InspectorTags.JAVA_DETECTED);

    }

    @Override
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
    private InspectorResult analyzeBinaryClass(ProjectFile projectFile, ResourceLocation binaryLocation)
            throws IOException {

        // Validate binary location before attempting to open stream
        if (binaryLocation == null) {
            return InspectorResult.error(getColumnName(),
                    "Binary location is null for project file: " + projectFile.getFilePath());
        }

        try (InputStream classStream = resourceResolver.openStream(binaryLocation)) {
            if (classStream == null) {
                return InspectorResult.error(getColumnName(),
                        "Could not open binary class stream: " + binaryLocation);
            }

            // Additional validation: check if stream is immediately at end
            if (!classStream.markSupported()) {
                // If mark not supported, we can't pre-check, so proceed to analyzeClassFile
                // which will handle empty streams
                return analyzeClassFile(projectFile, binaryLocation, classStream);
            }

            // Mark current position and try to read one byte to check if stream has content
            classStream.mark(1);
            int firstByte = classStream.read();
            classStream.reset();

            if (firstByte == -1) {
                return InspectorResult.error(getColumnName(),
                        "Empty class file stream: " + binaryLocation);
            }

            return analyzeClassFile(projectFile, binaryLocation, classStream);

        } catch (IOException e) {
            return InspectorResult.error(getColumnName(),
                    "Error accessing binary class file: " + binaryLocation + " - " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(),
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
     * @return the result of the analysis
     * @throws IOException if there's an error reading the class file
     */
    protected abstract InspectorResult analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
            InputStream classInputStream) throws IOException;
}
