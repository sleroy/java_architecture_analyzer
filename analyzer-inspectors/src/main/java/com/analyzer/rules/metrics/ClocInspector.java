package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.dev.inspectors.source.AbstractSourceFileInspector;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.resource.ResourceResolver;

import java.io.IOException;

/**
 * Inspector that counts lines of code (CLOC) in Java source files.
 * This is a concrete implementation of AbstractSourceFileInspector that
 * provides
 * basic line counting functionality for source code analysis.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED, InspectorTags.TAG_JAVA_IS_SOURCE }, produces = { ClocInspector.TAG_CLOC })
public class ClocInspector extends AbstractSourceFileInspector {

    public static final String TAG_CLOC = "metrics.cloc";

    /**
     * Creates a ClocInspector with the specified ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing source file resources
     */
    public ClocInspector(ResourceResolver resourceResolver, LocalCache localCache) {
        super(resourceResolver, localCache);
    }

    @Override
    public String getName() {
        return "Number of lines of code";
    }

    public String getColumnName() {
        return TAG_CLOC;
    }

    @Override
    protected void analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation,
            NodeDecorator<ProjectFile> decorator) throws IOException {
        try {
            long lineCount = countLines(sourceLocation);
            decorator.setMetric(getColumnName(), lineCount);
        } catch (IOException e) {
            decorator.error("Error counting lines: " + e.getMessage());
        }
    }
}
