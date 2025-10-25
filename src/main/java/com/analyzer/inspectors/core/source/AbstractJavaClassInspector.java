package com.analyzer.inspectors.core.source;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import javax.inject.Inject;

public abstract class AbstractJavaClassInspector extends AbstractJavaParserInspector {

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public AbstractJavaClassInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                          NodeDecorator nodeDecorator) {
        cu.getTypes().forEach(type -> {
            classNodeRepository.getOrCreateClassNode(type).ifPresent(classNode -> {
                classNode.setProjectFileId(projectFile.getId());
                analyzeClass(projectFile, classNode, type, nodeDecorator);
            });
        });
    }

    protected abstract void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                         NodeDecorator projectFileDecorator);
}
