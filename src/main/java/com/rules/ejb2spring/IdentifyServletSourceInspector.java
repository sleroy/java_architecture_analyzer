package com.rules.ejb2spring;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.source.JavaParserInspector;
import com.analyzer.inspectors.core.source.SourceFileInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;

public class IdentifyServletSourceInspector extends JavaParserInspector {

    protected IdentifyServletSourceInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected InspectorResult analyzeCompilationUnit(CompilationUnit cu, Clazz clazz) {
        return null;
    }

    @Override
    public String getName() {
        return "Identify Servlet";
    }

    @Override
    public String getColumnName() {
        return "is_servlet";
    }


}
