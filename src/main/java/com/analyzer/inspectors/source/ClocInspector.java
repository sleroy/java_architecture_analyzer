package com.analyzer.inspectors.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;

/**
 * Lines of Code (CLOC) inspector that counts the number of lines in source
 * files.
 * This is one of the default inspectors specified in purpose.md.
 * 
 * Extends SourceFileInspector to analyze source files only.
 */
public class ClocInspector extends SourceFileInspector {

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
            // Use the inherited countLines method from SourceFileInspector
            long lineCount = countLines(sourceLocation);
            return new InspectorResult(getName(), lineCount);
        } catch (Exception e) {
            return InspectorResult.error(getName(), "Error counting lines: " + e.getMessage());
        }
    }
}
