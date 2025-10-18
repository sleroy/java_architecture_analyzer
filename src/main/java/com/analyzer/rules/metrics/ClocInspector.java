package com.analyzer.rules.metrics;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractSourceFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;

/**
 * Inspector that counts lines of code (CLOC) in Java source files.
 * This is a concrete implementation of AbstractSourceFileInspector that provides
 * basic line counting functionality for source code analysis.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED }, produces = {ClocInspector.TAG_CLOC})
public class ClocInspector extends AbstractSourceFileInspector {

    public static final String TAG_CLOC = "cloc";

    /**
     * Creates a ClocInspector with the specified ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing source file resources
     */
    public ClocInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public String getName() {
        return "Number of lines of code";
    }


    public String getColumnName() {
        return TAG_CLOC;
    }

    @Override
    protected void analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation, ProjectFileDecorator projectFileDecorator) throws IOException {
        try {
            long lineCount = countLines(sourceLocation);
            projectFileDecorator.setTag(getColumnName(), lineCount);
        } catch (IOException e) {
            projectFileDecorator.error("Error counting lines: " + e.getMessage());
        }
    }
}
