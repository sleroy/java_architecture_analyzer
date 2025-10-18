package com.analyzer.inspectors.core.source;

import com.analyzer.core.export.ProjectFileDecorator;
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
                                          ProjectFileDecorator projectFileDecorator) {
        cu.getTypes().forEach(type -> {
            classNodeRepository.getOrCreateClassNode(type).ifPresent(classNode -> {
                classNode.setProjectFileId(projectFile.getId());
                analyzeClass(projectFile, classNode, type, projectFileDecorator);
            });
        });
    }

    protected abstract void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                         ProjectFileDecorator projectFileDecorator);
}
