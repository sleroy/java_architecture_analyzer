package com.analyzer.inspectors.core.source;

import com.analyzer.core.export.ResultDecorator;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import javax.inject.Inject;

public abstract class AbstractJavaClassInspector extends AbstractJavaParserInspector {

    private final GraphRepository graphRepository;

    @Inject
    public AbstractJavaClassInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        super(resourceResolver);
        this.graphRepository = graphRepository;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
            ResultDecorator resultDecorator) {
        String packageName = cu.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");

        cu.getTypes().forEach(type -> {
            String className = type.getNameAsString();
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            graphRepository.getNode(fullyQualifiedName).ifPresent(node -> {
                if (node instanceof JavaClassNode) {
                    analyzeClass((JavaClassNode) node, type, resultDecorator);
                }
            });
        });
    }

    protected abstract void analyzeClass(JavaClassNode classNode, TypeDeclaration<?> type,
            ResultDecorator resultDecorator);
}
