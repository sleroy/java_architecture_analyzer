package com.analyzer.dev.inspectors.source;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.resource.ResourceResolver;

import java.io.IOException;

/**
 * Abstract base class for source file inspectors that need access to the full
 * text content.
 * Returns the result of processing the complete file content as a string.
 * <p>
 * Subclasses must implement getName(), getColumnName(), and processContent()
 * methods.
 * The processContent method receives the full file content and the class being
 * analyzed.
 */
public abstract class AbstractTextFileInspector extends AbstractSourceFileInspector {

    /**
     * Creates a AbstractTextFileInspector with the specified ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing source file resources
     */
    protected AbstractTextFileInspector(ResourceResolver resourceResolver, LocalCache localCache) {
        super(resourceResolver, localCache);
    }

    @Override
    protected final void analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation,
            NodeDecorator<ProjectFile> decorator)
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            processContent(content, clazz, decorator);
        } catch (IOException e) {
            decorator.error("Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            decorator.error("Error processing file content: " + e.getMessage());
        }
    }

    /**
     * Processes the file content and returns an analysis result.
     * Subclasses implement specific content analysis logic here.
     * <p>
     * This method is called with the complete content of the source file and the
     * class being analyzed. Implementations can perform any kind of text analysis,
     * parsing, or extraction and return appropriate results.
     *
     * @param content   the complete content of the source file
     * @param clazz     the class being analyzed
     * @param decorator the decorator for setting properties and tags
     */
    protected abstract void processContent(String content, ProjectFile clazz, NodeDecorator<ProjectFile> decorator);
}
