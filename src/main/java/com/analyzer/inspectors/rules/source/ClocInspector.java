package com.analyzer.inspectors.rules.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.source.SourceFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;

/**
 * Inspector that counts lines of code (CLOC) in Java source files.
 * This is a concrete implementation of SourceFileInspector that provides
 * basic line counting functionality for source code analysis.
 */
public class ClocInspector extends SourceFileInspector {

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
        return "cloc";
    }

    @Override
    public String getColumnName() {
        return "lines_of_code";
    }

    @Override
    public String getDescription() {
        return "Counts the number of lines of code in source files";
    }

    @Override
    protected InspectorResult analyzeSourceFile(Clazz clazz, ResourceLocation sourceLocation) throws IOException {
        try {
            long lineCount = countLines(sourceLocation);
            return new InspectorResult(getName(), lineCount);
        } catch (IOException e) {
            return InspectorResult.error(getName(), "Error counting lines: " + e.getMessage());
        }
    }
}
