package com.analyzer.inspectors.core.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;

/**
 * Abstract base class for source file inspectors that need access to the full text content.
 * Returns the result of processing the complete file content as a string.
 * 
 * Subclasses must implement getName(), getColumnName(), and processContent() methods.
 * The processContent method receives the full file content and the class being analyzed.
 */
public abstract class TextFileInspector extends SourceFileInspector {

    /**
     * Creates a TextFileInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     */
    protected TextFileInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected final InspectorResult analyzeSourceFile(Clazz clazz, ResourceLocation sourceLocation) 
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            return processContent(content, clazz);
        } catch (IOException e) {
            return InspectorResult.error(getColumnName(), "Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "Error processing file content: " + e.getMessage());
        }
    }

    /**
     * Processes the file content and returns an analysis result.
     * Subclasses implement specific content analysis logic here.
     * 
     * This method is called with the complete content of the source file and the
     * class being analyzed. Implementations can perform any kind of text analysis,
     * parsing, or extraction and return appropriate results.
     * 
     * @param content the complete content of the source file
     * @param clazz the class being analyzed
     * @return the result of content processing
     */
    protected abstract InspectorResult processContent(String content, Clazz clazz);
}
