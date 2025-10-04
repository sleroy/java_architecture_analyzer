package com.analyzer.inspectors.core.source;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Base class for all source file inspectors using URI-based ResourceResolver
 * system.
 * Provides common functionality for analyzing Java source files from various
 * sources.
 */
public abstract class SourceFileInspector implements Inspector<ProjectFile> {

    private final ResourceResolver resourceResolver;

    protected SourceFileInspector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public final InspectorResult decorate(ProjectFile projectFile) {
        if (!supports(projectFile)) {
            return InspectorResult.notApplicable(getColumnName());
        }

        try {
            // Create ResourceLocation from the project file path
            ResourceLocation sourceLocation = new ResourceLocation(projectFile.getFilePath().toUri());

            if (!resourceResolver.exists(sourceLocation)) {
                return InspectorResult.error(getColumnName(), "Source file not found: " + sourceLocation);
            }

            return analyzeSourceFile(projectFile, sourceLocation);

        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "Error analyzing source file: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        return projectFile != null && projectFile.hasSourceCode();
    }

    /**
     * Analyzes the source file for the given class.
     * Subclasses must implement this method to provide specific analysis logic.
     * 
     * @param projectFile    the project file to analyze
     * @param sourceLocation the ResourceLocation of the source file
     * @return the result of the analysis
     * @throws IOException if there's an error reading the source file
     */
    protected abstract InspectorResult analyzeSourceFile(ProjectFile projectFile, ResourceLocation sourceLocation)
            throws IOException;

    /**
     * Reads the entire content of the source file as a string using UTF-8 encoding.
     * 
     * @param sourceLocation the ResourceLocation of the source file
     * @return the content of the file
     * @throws IOException if there's an error reading the file
     */
    protected String readFileContent(ResourceLocation sourceLocation) throws IOException {
        try (InputStream inputStream = resourceResolver.openStream(sourceLocation)) {
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
     * Counts the number of lines in the source file.
     * 
     * @param sourceLocation the ResourceLocation of the source file
     * @return the number of lines
     * @throws IOException if there's an error reading the file
     */
    protected long countLines(ResourceLocation sourceLocation) throws IOException {
        try (InputStream inputStream = resourceResolver.openStream(sourceLocation);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            return reader.lines().count();
        }
    }

    /**
     * Gets the ResourceResolver used by this inspector.
     * 
     * @return the ResourceResolver instance
     */
    protected ResourceResolver getResourceResolver() {
        return resourceResolver;
    }
}
