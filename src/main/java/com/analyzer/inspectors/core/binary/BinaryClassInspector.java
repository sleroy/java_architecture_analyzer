package com.analyzer.inspectors.core.binary;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

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
        if (!projectFile.hasBinaryCode() && !projectFile.getFilePath().toString().endsWith(".class")) {
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
    public boolean supports(ProjectFile projectFile) {
        return projectFile != null &&
                (projectFile.hasBinaryCode() ||
                        projectFile.getFileExtension().equals("class") ||
                        projectFile.getFilePath().toString().endsWith(".class"));
    }

    /**
     * Analyzes a binary class using the ResourceResolver.
     */
    private InspectorResult analyzeBinaryClass(ProjectFile projectFile, ResourceLocation binaryLocation)
            throws IOException {
        try (InputStream classStream = resourceResolver.openStream(binaryLocation)) {
            if (classStream == null) {
                return InspectorResult.error(getColumnName(),
                        "Could not open binary class: " + binaryLocation);
            }
            return analyzeClassFile(projectFile, binaryLocation, classStream);
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
